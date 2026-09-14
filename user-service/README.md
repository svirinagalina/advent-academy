# User Service with RBAC

Production-ready microservice for user management with Role-Based Access Control (RBAC), authentication, and Multi-Factor Authentication (MFA) support.

## Features

- **User Management**: Registration, profile management, soft delete
- **Authentication**: JWT-based authentication with refresh tokens
- **RBAC**: Comprehensive role and permission management
- **Account Security**:
  - Account locking after failed login attempts (5 attempts, 30-minute lock)
  - Password hashing with BCrypt
  - Email verification and password reset with one-time, digest-only, expiring tokens
  - TOTP MFA (Google Authenticator, Authy, Microsoft Authenticator) with encrypted secrets,
    replay protection, one-time recovery codes and step-up (password) on enable/disable
  - Rate limiting of the public auth endpoints per client address and per account, shared
    across replicas through the database
- **Clean Architecture**: Domain-driven design with clear separation of concerns
- **REST API**: Comprehensive API with OpenAPI/Swagger documentation
- **Database**: PostgreSQL with JPA/Hibernate
- **Workflow**: Camunda 8 role-approval process
- **Docker**: Production-ready Docker configuration

## Technology Stack

- Java 21
- Spring Boot 3.4.1
- Camunda 8.7 / Zeebe
- PostgreSQL 16
- Spring Security (configured as stub)
- JWT (jjwt library - stub implementation)
- OpenAPI/Swagger (springdoc-openapi)
- Docker & Docker Compose
- JUnit 5, Mockito, Testcontainers

## Architecture

The project follows Clean Architecture principles:

```
com.adventacademy.userservice
├── domain/           # Core business logic (entities, exceptions, repository interfaces)
├── application/      # Use cases and business services
├── infrastructure/   # Technical details (JPA, security implementations)
└── adapter/          # REST controllers and configurations
```

## Getting Started

### Prerequisites

- Java 21
- Docker & Docker Compose
- Gradle (or use included wrapper)

### Local Camunda 8

Copy the environment template once, then start `user-service` with its local Camunda dependencies:

```bash
cp -n .env.example .env
docker compose up -d --build postgres elasticsearch zeebe operate tasklist user-service
```

The local endpoints are:

- User Service: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Operate: http://localhost:8082
- Tasklist: http://localhost:8083
- Zeebe gRPC: `localhost:26500`
- Zeebe REST: http://localhost:8088
- Zeebe management health: http://localhost:9600/actuator/health

Operate and Tasklist use the local `demo` / `demo` credentials. The Zeebe gRPC and REST APIs do
not require authentication in this development-only setup.

Check container state and the connection reported by `user-service`:

```bash
docker compose ps
curl -fsS http://localhost:9600/actuator/health
curl -fsS http://localhost:8080/actuator/health/readiness
curl -fsS http://localhost:8088/v2/topology
```

The `local` and `docker` profiles deploy `bpmn/role-approval.bpmn` automatically during application
startup. Start an instance and inspect it in Operate or Tasklist:

```bash
curl -fsS -X POST http://localhost:8088/v2/process-instances \
  -H 'Content-Type: application/json' \
  -d '{"processDefinitionId":"role-approval","variables":{"userId":"local-user","requestedRole":"MENTOR"}}'
```

The instance stops at `Review role request`, which makes it visible in Tasklist.

Stop the stack without deleting process and database data:

```bash
docker compose down
```

### Role approval smoke test

With the stack above running, exercise the full approve/reject flow through the actual
application API (not the raw Zeebe API used above) — submit a request, have an admin decide
on it, and watch the process complete in Tasklist/Operate.

Submit a role request for an existing user and role (see `db/demo/demo_users_seed.sql` for
ready-made ids, or create your own via `/api/auth/register` and `/api/roles`):

```bash
curl -fsS -X POST http://localhost:8080/api/users/{userId}/role-requests \
  -H 'Content-Type: application/json' \
  -d '{"requestedRoleId":"{roleId}"}'
```

The response's `id` is the role request id; the request now has a real Camunda process instance
behind it, waiting at the `Review role request` user task — visible in Tasklist. Record an admin
decision (`APPROVE` grants the role, `REJECT` leaves it untouched):

```bash
curl -fsS -X POST http://localhost:8080/api/role-requests/{roleRequestId}/decision \
  -H 'Content-Type: application/json' \
  -d '{"decision":"APPROVE","reviewerId":"{adminUserId}"}'
```

This returns `202 Accepted` with the request still `PENDING` — the actual transition happens
asynchronously once the corresponding job worker (`ApproveRoleRequestWorker`/
`RejectRoleRequestWorker`) picks up the routed job. There is no single-request lookup endpoint;
poll the user's request list (or `GET /api/role-requests` for the admin view) until it settles:

```bash
curl -fsS http://localhost:8080/api/users/{userId}/role-requests
```

`status` moves to `APPROVED` or `REJECTED` within a second or two; for `APPROVE`, the target
user's roles (`GET /api/users/{userId}`) now include the requested role. The completed process
instance is visible in Operate. Submitting the same decision twice (a redelivered job, or a
manual retry) is safe — both workers are idempotent and will not reassign the role or flip an
already-decided request again.

### Running Locally (Development)

Run the infrastructure in Docker and `user-service` on the host:

```bash
docker compose up -d postgres elasticsearch zeebe operate tasklist
set -a
source .env
set +a
./gradlew :user-service:bootRun --args='--spring.profiles.active=local'
```

### Running Tests

Ordinary tests use the `test` profile and do not connect to the shared local Camunda stack. The
`RoleApprovalProcessTest` starts an isolated Zeebe Testcontainer, deploys `role-approval.bpmn`, and
checks that a process instance reaches the review user task. Docker must be available:

```bash
./gradlew :user-service:test
```

The opt-in smoke test requires PostgreSQL and Zeebe to be running and verifies that the Spring
application starts, deploys BPMN, reads broker topology, starts `role-approval`, and cancels the
test instance after verification:

```bash
docker compose up -d postgres elasticsearch zeebe
set -a
source .env
set +a
./gradlew :user-service:camundaSmokeTest
```

## API Endpoints

### Authentication
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - User login
- `POST /api/auth/refresh` - Refresh access token
- `POST /api/auth/confirm-email?token=` - Confirm email address (token from the verification e-mail; single use, 24 h)
- `POST /api/auth/resend-verification` - Re-send the verification e-mail (`{email}`; identical answer for any address)
- `POST /api/auth/change-password` - Change password (access token; revokes all refresh sessions)
- `POST /api/auth/password-reset` - Request a reset link (`{email}`; identical answer for any address)
- `POST /api/auth/password-reset/confirm` - Set a new password (`{token, newPassword, confirmPassword}`; single use, 1 h; revokes all refresh sessions)
- `POST /api/auth/mfa/enable` - Start MFA enrolment (`{password}`; returns `secret`, `qrCodeUrl`, `qrCodeImage`)
- `POST /api/auth/mfa/confirm` - Confirm enrolment (`{code}`; returns the recovery codes exactly once)
- `POST /api/auth/mfa/disable` - Disable MFA (`{password, code}`; `code` is a TOTP or an unused recovery code)

Login with MFA: `POST /api/auth/login` without `mfaCode` answers `{"mfaRequired": true}`; repeat with
`mfaCode` set to the six-digit TOTP or to a recovery code (`XXXXX-XXXXX`). Each TOTP and each
recovery code is accepted once. Public auth endpoints answer `429` with `Retry-After` when the
per-address or per-account quota is used up.

### Users
- `GET /api/users` - Get all users
- `GET /api/users/{id}` - Get user by ID
- `DELETE /api/users/{id}` - Soft delete user
- `POST /api/users/{id}/unlock` - Unlock user account

### Roles
- `POST /api/roles` - Create role
- `GET /api/roles` - Get all roles
- `GET /api/roles/{id}` - Get role by ID
- `POST /api/roles/users/{userId}/roles/{roleId}` - Assign role to user
- `DELETE /api/roles/users/{userId}/roles/{roleId}` - Remove role from user

### Permissions
- `POST /api/permissions` - Create permission
- `GET /api/permissions` - Get all permissions
- `GET /api/permissions/{id}` - Get permission by ID

## Configuration

Key configuration properties in `application.yml`:

```yaml
security:
  jwt:
    secret: ${JWT_SECRET:your-256-bit-secret}
    access-token-expiration-minutes: 15
    refresh-token-expiration-days: 7
  account-lock:
    max-failed-attempts: 5
    lock-duration-minutes: 30
  mfa:
    totp-window-size: 1              # ±1 step of 30 s
    recovery-code-count: 8
    encryption:                      # AES-256-GCM keys for TOTP secrets at rest, versioned
      active-key: v1
      keys:
        v1: ${MFA_ENCRYPTION_KEY_V1} # base64, 32 bytes: `openssl rand -base64 32`; no default
  tokens:
    email-verification-ttl: 24h
    password-reset-ttl: 1h
  rate-limit:                        # fixed windows, counters in auth_rate_limit_buckets
    window: 1h
    login-per-ip: 20
    login-per-account: 10
    register-per-ip: 10
    password-reset-per-ip: 5
    password-reset-per-account: 3
    resend-verification-per-ip: 5
    resend-verification-per-account: 3

spring:
  mail:
    enabled: ${MAIL_ENABLED:false}   # false = log-only mock adapter, true = SMTP (SMTP_HOST, SMTP_PORT, ...)

server:
  forward-headers-strategy: ${FORWARD_HEADERS_STRATEGY:none}   # `native` behind the edge proxy
  tomcat:
    remoteip:
      internal-proxies: ${TRUSTED_PROXIES:}                    # regex of proxy addresses to trust

camunda:
  client:
    mode: self-managed
    zeebe:
      enabled: true
      grpc-address: http://localhost:26500
      rest-address: http://localhost:8088
```

## Key rotation for MFA secrets

Add the new key as `security.mfa.encryption.keys.v2`, switch `active-key` to `v2` and keep `v1`
configured until every stored secret has been re-encrypted (users re-enrolling, or a maintenance
job); then remove `v1`. Values carry their key version (`v1:<nonce>:<ciphertext>`), so both keys
are readable during the transition.

## Testing

The project includes:
- Unit tests for domain logic
- Unit tests for services with Mockito
- Integration tests for controllers
- Testcontainers for integration tests (PostgreSQL)
- Opt-in Camunda connection smoke test

## User Status Flow

```
PENDING (registration) → ACTIVE (email confirmed)
                      ↓
                   LOCKED (failed attempts)
                      ↓
                   ACTIVE (unlocked by admin)
                      ↓
               SUSPENDED/BLOCKED (by admin)
                      ↓
                   DELETED (soft delete)
```

## Security Notes

- All passwords are hashed using BCrypt
- Account locks after 5 failed login attempts for 30 minutes
- JWT tokens expire after 15 minutes (configurable)
- Refresh tokens expire after 7 days (configurable)
- TOTP secrets are encrypted at rest; TOTP codes and recovery codes are single-use
- Verification/reset tokens exist in plaintext only inside the e-mail; the database keeps digests
- Password reset/change revokes all refresh sessions
- Public auth endpoints do not reveal whether an account exists and are rate limited
- Soft delete for user accounts

## License

MIT License
