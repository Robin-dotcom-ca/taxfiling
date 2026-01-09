# Tax Filing Backend Service

A Spring Boot backend service for a simplified tax filing platform.

## Prerequisites

- Java 21 or higher
- PostgreSQL 15+
- Gradle 8.5+ (wrapper included)

## Getting Started

### 1. Clone and Setup

```bash
cd taxfiling
```

### 2. Database Setup

Create a PostgreSQL database:

```sql
CREATE DATABASE taxfiling;
CREATE USER taxfiling_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE taxfiling TO taxfiling_user;
```

### 3. Configure Environment

Set environment variables or update `application.yml`:

```bash
export DB_USERNAME=taxfiling_user
export DB_PASSWORD=your_password
export JWT_SECRET=your-256-bit-secret-key
```

### 4. Build

```bash
# Windows
gradlew.bat build

# Linux/Mac
./gradlew build
```

### 5. Run

```bash
# Windows
gradlew.bat bootRun

# Linux/Mac
./gradlew bootRun
```

The service will start at `http://localhost:8080`

## API Documentation

Once running, access the Swagger UI at:
- http://localhost:8080/swagger-ui.html

API docs (OpenAPI JSON):
- http://localhost:8080/api-docs

## Testing

```bash
# Run all tests
gradlew.bat test

# Run with coverage
gradlew.bat test jacocoTestReport
```

## Project Structure

```
src/main/java/com/taxfiling/
├── TaxFilingApplication.java    # Main application entry point
├── config/                      # Configuration classes
├── controller/                  # REST API controllers
├── service/                     # Business logic
├── repository/                  # Data access layer
├── model/                       # Domain entities
├── dto/                         # Data transfer objects
├── exception/                   # Custom exceptions
└── security/                    # Security configuration
```

## Tech Stack

- **Framework**: Spring Boot 3.2
- **Database**: PostgreSQL with Spring Data JPA
- **Security**: Spring Security with JWT
- **Documentation**: SpringDoc OpenAPI (Swagger)
- **Build**: Gradle 8.5 (Kotlin DSL)

## License

Private - All rights reserved
