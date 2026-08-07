# User Service with RBAC

Production-ready microservice for user management with Role-Based Access Control (RBAC), authentication, and Multi-Factor Authentication (MFA) support.

## Features

- **User Management**: Registration, profile management, soft delete
- **Authentication**: JWT-based authentication (stub implementation with TODO)
- **RBAC**: Comprehensive role and permission management
- **Account Security**:
  - Account locking after failed login attempts (5 attempts, 30-minute lock)
  - Password hashing with BCrypt
  - Email verification (stub implementation with TODO)
  - MFA support (stub implementation with TODO)
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
- `POST /api/auth/confirm-email` - Confirm email address
- `POST /api/auth/change-password` - Change password
- `POST /api/auth/mfa/enable` - Enable MFA
- `POST /api/auth/mfa/confirm` - Confirm MFA setup
- `POST /api/auth/mfa/disable` - Disable MFA

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

camunda:
  client:
    mode: self-managed
    zeebe:
      enabled: true
      grpc-address: http://localhost:26500
      rest-address: http://localhost:8088
```

## TODO: Future Implementation

The following features are stubbed with TODO comments and need implementation:

1. **JWT Provider** (`JwtProviderStub.java`):
   - Implement JWT token generation and validation using jjwt library
   - Add proper token signing and verification
   - Implement token expiration handling

2. **MFA Provider** (`MfaProviderStub.java`):
   - Implement TOTP (Time-based One-Time Password)
   - Generate QR codes for authenticator apps
   - Validate MFA codes

3. **Spring Security** (`SecurityConfigStub.java`):
   - Configure JWT authentication filter
   - Set up authorization rules
   - Add CORS configuration
   - Implement authentication entry point

4. **Email Service**:
   - Send email verification on registration
   - Send password reset emails
   - Email templates

5. **Additional Features**:
   - Refresh token storage (database or Redis)
   - Password reset flow
   - Audit logging
   - Rate limiting for login endpoint

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
- MFA support for additional security
- Soft delete for user accounts

## License

MIT License
