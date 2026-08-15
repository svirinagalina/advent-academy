# Advent Academy

Advent Academy is an educational platform focused on teaching programming and software engineering,
with a primary emphasis on Java and backend development.

The project is designed as both a real product and a learning environment,
demonstrating proper architecture, clean code principles, and scalable system design.

---

## 🎯 Project Goals

- Build a foundation for an educational platform (LMS)
- Provide structured courses with theory and practical assignments
- Emphasize architectural thinking over framework-driven development
- Serve as a reference project for Clean Architecture and DDD principles
- Prepare the codebase for future scaling and modularization

---

## 🧱 Architecture

The project follows **Clean Architecture** principles.

### Application layers:
com.adventacademy

├─ domain

├─ application

├─ adapters

└─ infrastructure

### Key rules:

- `domain` does not depend on any framework
- `application` depends only on the domain
- `adapters` and `infrastructure` depend on outer layers
- Business logic is isolated from technical concerns

---

## 🛠 Technology Stack

- **Java 21**
- **Spring Boot**
- **Gradle**
- **JUnit / Testcontainers** (planned)
- **PostgreSQL / Kafka / Redis** (planned)

The technology stack may evolve as the project grows.

---

## 📦 Dependency management

Every module (`user-service`, `content-service`, `meeting-service`, `api-gateway`) consumes one
project-wide dependency BOM instead of pinning its own library versions. Two mechanisms, because
they centralize two different things Gradle resolves at different times:

- **`platform/build.gradle`** (Gradle's built-in `java-platform` plugin) — the actual BOM.
  Manages *dependency artifact* versions: Camunda, Springdoc, JWT, CommonMark, OWASP HTML
  Sanitizer, p6spy, Testcontainers, ArchUnit, WireMock, plus Spring Boot's and Spring Cloud's own
  BOMs (imported via `api platform(...)`). Every module depends on it with
  `implementation platform(project(':platform'))`.
- **`gradle/libs.versions.toml`** (Gradle Version Catalog) — manages *plugin* versions
  (`org.springframework.boot`, `com.github.spotbugs`) and the checkstyle tool version. A
  `java-platform` BOM can't manage plugin versions at all — they're resolved before the
  dependency graph exists — so this is a separate, complementary file.

**`io.spring.dependency-management` is deliberately not used anywhere in this project.** It
doesn't participate in Gradle's normal constraint-conflict resolution: testing showed it
overriding `:platform`'s versions even with a `strictly()` constraint, which is what caused the
Testcontainers version split this BOM was introduced to fix in the first place. Every module
keeps only the `org.springframework.boot` plugin (needed for `bootJar`/`bootRun`); Spring Boot's
own BOM reaches every module exclusively through `:platform`.

### Adding a new managed dependency

Add a constraint to `platform/build.gradle`'s `constraints { }` block:

```groovy
constraints {
    api 'some.group:some-artifact:1.2.3'
}
```

Then reference it **unversioned** in the consuming module's `dependencies { }` block:
`implementation 'some.group:some-artifact'`.

### Bumping an existing version

Change it once, in `platform/build.gradle` — never in a service's own `build.gradle`. Confirm
the new version actually took effect:

```bash
./gradlew :<module>:dependencyInsight --dependency <group>:<artifact>
```

### A Gradle quirk worth knowing

`annotationProcessor` and `developmentOnly` are separate root configurations that do **not**
extend `implementation` — a module using either one (for Lombok, the Boot configuration
processor, or spring-boot-devtools) needs its own explicit
`annotationProcessor platform(project(':platform'))` / `developmentOnly platform(project(':platform'))`
line, or those dependencies resolve with no version at all. Every module that uses either
configuration already has this; keep it in mind when adding a new one.

---

## 🚀 Getting Started

### Prerequisites

- JDK 21
- Gradle (wrapper included)
- Docker with Docker Compose v2

This project is licensed under the terms of the [MIT License](LICENSE).

### Run the application

```bash
./gradlew clean build
./gradlew :user-service:bootRun --args='--spring.profiles.active=local'
```

### Run the local meeting MVP

The dependency-free meeting UI, REST room API, and WebSocket signaling endpoint are served by
`meeting-service` on port `8084`:

```bash
./gradlew :meeting-service:bootRun
```

Open http://localhost:8084 in two browser tabs. Detailed architecture, protocol, testing, and
troubleshooting guidance is in [docs/meeting-service.md](docs/meeting-service.md).

### Run the Markdown preview UI

`content-service` serves a dependency-free static page for checking how a Markdown file renders
to sanitized HTML before it's ever uploaded — nothing typed or previewed there is saved to the
database:

```bash
docker compose up -d postgres
./gradlew :content-service:bootRun --args='--spring.profiles.active=local'
```

Open http://localhost:8081/markdown-preview/ directly, or
http://localhost:8090/markdown-preview/ through `api-gateway` (`./gradlew :api-gateway:bootRun`)
once all backing services are up. The page posts to `POST /api/markdown/preview`, which reuses
the same file-size/UTF-8/extension validation and OWASP HTML sanitization as the real lesson
content upload endpoint.

### Run user-service with Camunda 8

Create local environment settings and start PostgreSQL, Zeebe, Operate, and Tasklist:

```bash
cp -n .env.example .env
docker compose up -d postgres elasticsearch zeebe operate tasklist
set -a
source .env
set +a
./gradlew :user-service:bootRun --args='--spring.profiles.active=local'
```

The `role-approval.bpmn` process is deployed automatically when `user-service` starts with the
`local` or `docker` profile. Detailed startup, verification, and process-instance commands are in
[user-service/README.md](user-service/README.md#local-camunda-8).
