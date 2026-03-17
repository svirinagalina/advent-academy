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
- **Docker**: Production-ready Docker configuration

## Technology Stack

- Java 17
- Spring Boot 4.0.3
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

- Java 17
- Docker & Docker Compose
- Gradle (or use included wrapper)

### Running with Docker Compose

1. Start the services:
```bash
docker-compose up --build
```

2. The service will be available at:
   - API: http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - Health check: http://localhost:8080/actuator/health

### Running Locally (Development)

1. Start PostgreSQL:
```bash
docker-compose up postgres -d
```

2. Run the application:
```bash
./gradlew bootRun
```

### Running Tests

```bash
./gradlew test
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
