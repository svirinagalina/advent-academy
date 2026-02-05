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

## 🚀 Getting Started

### Prerequisites

- JDK 21
- Gradle (wrapper included)

This project is licensed under the terms of the [MIT License](LICENSE).

### Run the application

```bash
./gradlew clean build
./gradlew bootRun