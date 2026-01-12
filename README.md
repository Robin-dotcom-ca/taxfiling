# Tax Filing Backend Service

A Spring Boot backend service for a simplified tax filing platform. This service provides REST APIs for taxpayers to create, manage, and submit tax filings with full calculation transparency and audit trails.

## Features

- **User Authentication**: JWT-based authentication with access/refresh tokens
- **Tax Filing Management**: Create, update, and submit tax filings
- **Progressive Tax Calculation**: Multi-bracket tax calculation with full breakdown
- **Deductions & Credits**: Support for RRSP, charitable donations, and various tax credits
- **Amendment Support**: Create amendments for submitted filings
- **Calculation Explainability**: Step-by-step calculation trace for transparency
- **Tax Rule Versioning**: Admin-managed tax rules with version control (DRAFT → ACTIVE → DEPRECATED)
- **Audit Trail**: Complete history of all changes to filings and rules
- **Pagination**: All list endpoints support pagination
- **Observability**: Structured JSON logging, Prometheus metrics, request tracing, health checks

## Tech Stack

- **Framework**: Spring Boot 3.2.2
- **Language**: Java 21
- **Database**: PostgreSQL 15+ with JSONB support
- **ORM**: Spring Data JPA with Hibernate
- **Migrations**: Flyway
- **Security**: Spring Security with JWT (jjwt 0.12.3)
- **Documentation**: SpringDoc OpenAPI 2.3.0 (Swagger UI)
- **Observability**: Micrometer + Prometheus, Logback with JSON encoder, Spring AOP
- **Build**: Gradle 8.5 (Kotlin DSL)
- **Testing**: JUnit 5, Mockito, AssertJ

## Prerequisites

- Java 21 or higher
- PostgreSQL 15+
- Gradle 8.5+ (wrapper included)
- Docker & Docker Compose (optional, for containerized deployment)

## Quick Start with Docker

The fastest way to get started is using Docker Compose:

```bash
# Start everything (PostgreSQL + Application)
docker-compose up -d

# View logs
docker-compose logs -f app

# Stop everything
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

The application will be available at:
- **API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **PostgreSQL**: localhost:5432

### Docker Compose Services

| Service | Container Name | Port | Description |
|---------|---------------|------|-------------|
| `postgres` | taxfiling-db | 5432 | PostgreSQL 16 database |
| `app` | taxfiling-app | 8080 | Spring Boot application |

### Docker Commands

```bash
# Build only (without starting)
docker-compose build

# Rebuild and start
docker-compose up -d --build

# View running containers
docker-compose ps

# Execute command in app container
docker-compose exec app sh

# View database logs
docker-compose logs postgres

# Scale (if needed)
docker-compose up -d --scale app=2
```

### Development with Docker (Database Only)

Run only PostgreSQL in Docker while developing locally:

```bash
# Start only PostgreSQL
docker-compose up -d postgres

# Run app locally
./gradlew bootRun
```

### Docker Environment Variables

The `docker-compose.yml` configures these environment variables for the app:

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | jdbc:postgresql://postgres:5432/taxfiling | Database URL |
| `SPRING_DATASOURCE_USERNAME` | postgres | Database user |
| `SPRING_DATASOURCE_PASSWORD` | postgres | Database password |
| `APP_JWT_SECRET` | (set in compose) | JWT signing key |

For production, override these in a `.env` file or pass them directly:

```bash
# Using .env file
echo "APP_JWT_SECRET=your-production-secret-key-256-bits" > .env
docker-compose up -d

# Or inline
APP_JWT_SECRET=your-secret docker-compose up -d
```

## Getting Started (Manual Setup)

### 1. Database Setup

Create a PostgreSQL database:

```sql
CREATE DATABASE taxfiling;
CREATE USER taxfiling_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE taxfiling TO taxfiling_user;
-- For PostgreSQL 15+, also grant schema permissions:
\c taxfiling
GRANT ALL ON SCHEMA public TO taxfiling_user;
```

### 2. Configure Environment

Set environment variables or create an `application-local.yml`:

```bash
# Required
export DB_USERNAME=taxfiling_user
export DB_PASSWORD=your_password
export JWT_SECRET=your-256-bit-secret-key-for-jwt-signing

# Optional (defaults shown)
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=taxfiling
```

Or create `src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/taxfiling
    username: taxfiling_user
    password: your_password

app:
  jwt:
    secret: your-256-bit-secret-key-for-jwt-signing
```

### 3. Build

```bash
# Windows
gradlew.bat build

# Linux/Mac
./gradlew build
```

### 4. Run

```bash
# Windows
gradlew.bat bootRun

# Linux/Mac
./gradlew bootRun

# Or with a specific profile
./gradlew bootRun --args='--spring.profiles.active=local'
```

The service will start at `http://localhost:8080`

## Sample Data for Testing

The application can be started with pre-loaded sample data for testing all services:

### Quick Start with Sample Data

```bash
# Using Gradle (with local PostgreSQL)
./gradlew bootRun --args='--spring.profiles.active=sample-data'

# Using Docker Compose
SPRING_PROFILES_ACTIVE=docker,sample-data docker-compose up -d
```

### Sample Users

| Email | Password | Role |
|-------|----------|------|
| `admin@taxfiling.com` | `Admin123!` | ADMIN |
| `john.doe@example.com` | `Password123!` | TAXPAYER |
| `jane.smith@example.com` | `Password123!` | TAXPAYER |

### Sample Data Included

**Tax Rules (2024 Canadian Federal)**:
- 5 progressive tax brackets (15% to 33%)
- Credits: Basic Personal Amount, Spouse Amount, Canada Employment, GST/HST (refundable), Climate Action (refundable)
- Deductions: RRSP, Union Dues, Childcare, Moving Expenses, Charitable

**Sample Filings**:
- **John Doe (2024)**: Employment ($85k), Investment ($2.5k), Interest ($500), RRSP deduction ($10k), Charitable ($500)
- **Jane Smith (2024)**: Employment ($120k), RRSP deduction ($15k)
- **John Doe (2023)**: Employment ($75k) - previous year

### Testing Workflow

```bash
# 1. Login as taxpayer
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "john.doe@example.com", "password": "Password123!"}'

# 2. Get filings (use token from login response)
curl http://localhost:8080/api/v1/filings \
  -H "Authorization: Bearer <token>"

# 3. Calculate tax for a filing
curl -X POST http://localhost:8080/api/v1/filings/<filing_id>/calculations \
  -H "Authorization: Bearer <token>"

# 4. Submit filing
curl -X POST http://localhost:8080/api/v1/filings/<filing_id>/submit \
  -H "Authorization: Bearer <token>"
```

### Admin Testing

```bash
# Login as admin
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@taxfiling.com", "password": "Admin123!"}'

# View tax rules
curl http://localhost:8080/api/v1/admin/tax-rules/status/ACTIVE \
  -H "Authorization: Bearer <admin_token>"

# View audit trail
curl http://localhost:8080/api/v1/admin/audit/type/tax_filing \
  -H "Authorization: Bearer <admin_token>"
```

## Testing

```bash
# Run all tests (uses H2 in-memory database)
gradlew.bat test

# Run a specific test class
gradlew.bat test --tests "com.taxfiling.service.CalculationServiceTest"

# Run a specific test method
gradlew.bat test --tests "com.taxfiling.service.CalculationServiceTest.CalculateTaxTests.shouldCalculateSimpleTax"

# Run with coverage report
gradlew.bat test jacocoTestReport
# Coverage report: build/reports/jacoco/test/html/index.html
```

## API Documentation

### Interactive Documentation

Once running, access the Swagger UI at:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

### Postman Collection

A complete Postman collection is available in the `postman/` folder with all API endpoints pre-configured:

```
postman/
└── Tax_Filing_API.postman_collection.json
```

**Import into Postman:**
1. Open Postman
2. Click **Import** → **Upload Files**
3. Select the collection JSON file
4. Set environment variable `baseUrl` to `http://localhost:8080`

### Authentication Endpoints

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/v1/auth/register` | Register new user | No |
| POST | `/api/v1/auth/login` | Login and get tokens | No |
| POST | `/api/v1/auth/refresh` | Refresh access token | No |

### Tax Filing Endpoints

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/v1/filings` | Create new filing | Yes |
| GET | `/api/v1/filings` | Get all my filings (paginated) | Yes |
| GET | `/api/v1/filings/{id}` | Get filing by ID | Yes |
| DELETE | `/api/v1/filings/{id}` | Delete draft filing | Yes |
| GET | `/api/v1/filings/status/{status}` | Get filings by status (paginated) | Yes |
| GET | `/api/v1/filings/year/{taxYear}` | Get filings for year (paginated) | Yes |
| POST | `/api/v1/filings/{id}/amend` | Create amendment | Yes |
| GET | `/api/v1/filings/{id}/amendments` | Get amendments (paginated) | Yes |

### Filing Items (Income, Deductions, Credits)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/v1/filings/{id}/income-items` | Add income item | Yes |
| PUT | `/api/v1/filings/{id}/income-items/{itemId}` | Update income item | Yes |
| DELETE | `/api/v1/filings/{id}/income-items/{itemId}` | Remove income item | Yes |
| POST | `/api/v1/filings/{id}/deduction-items` | Add deduction | Yes |
| PUT | `/api/v1/filings/{id}/deduction-items/{itemId}` | Update deduction | Yes |
| DELETE | `/api/v1/filings/{id}/deduction-items/{itemId}` | Remove deduction | Yes |
| POST | `/api/v1/filings/{id}/credit-claims` | Add credit claim | Yes |
| PUT | `/api/v1/filings/{id}/credit-claims/{claimId}` | Update credit claim | Yes |
| DELETE | `/api/v1/filings/{id}/credit-claims/{claimId}` | Remove credit claim | Yes |

### Tax Calculation Endpoints

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/v1/filings/{filingId}/calculations` | Calculate tax | Yes |
| GET | `/api/v1/filings/{filingId}/calculations/latest` | Get latest calculation | Yes |
| GET | `/api/v1/filings/{filingId}/calculations` | Get calculation history (paginated) | Yes |

### Submission Endpoints

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/v1/filings/{filingId}/submit` | Submit filing | Yes |
| GET | `/api/v1/filings/{filingId}/submission` | Get submission for filing | Yes |
| GET | `/api/v1/submissions` | Get all my submissions (paginated) | Yes |
| GET | `/api/v1/submissions/confirmation/{number}` | Get by confirmation number | Yes |

### Public Tax Rule Endpoints

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/tax-rules/active` | Get active rule for jurisdiction/year | Yes |
| GET | `/api/v1/tax-rules/{id}` | Get tax rule by ID | Yes |

### Admin: Tax Rule Management

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/v1/admin/tax-rules` | Create tax rule version | Admin |
| GET | `/api/v1/admin/tax-rules/{id}` | Get tax rule version | Admin |
| GET | `/api/v1/admin/tax-rules/active` | Get active rule | Admin |
| GET | `/api/v1/admin/tax-rules/jurisdiction/{j}/year/{y}` | Get rules for jurisdiction/year (paginated) | Admin |
| GET | `/api/v1/admin/tax-rules/status/{status}` | Get rules by status (paginated) | Admin |
| GET | `/api/v1/admin/tax-rules/jurisdiction/{j}` | Get rules by jurisdiction (paginated) | Admin |
| POST | `/api/v1/admin/tax-rules/{id}/activate` | Activate draft rule | Admin |
| POST | `/api/v1/admin/tax-rules/{id}/deprecate` | Deprecate rule | Admin |
| POST | `/api/v1/admin/tax-rules/{id}/brackets` | Add tax bracket | Admin |
| DELETE | `/api/v1/admin/tax-rules/{id}/brackets/{bracketId}` | Delete bracket | Admin |
| POST | `/api/v1/admin/tax-rules/{id}/credit-rules` | Add credit rule | Admin |
| DELETE | `/api/v1/admin/tax-rules/{id}/credit-rules/{creditRuleId}` | Delete credit rule | Admin |
| POST | `/api/v1/admin/tax-rules/{id}/deduction-rules` | Add deduction rule | Admin |
| DELETE | `/api/v1/admin/tax-rules/{id}/deduction-rules/{ruleId}` | Delete deduction rule | Admin |

### Admin: Audit Trail

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/admin/audit/entity/{type}/{id}` | Get audit history for entity | Admin |
| GET | `/api/v1/admin/audit/actor/{actorId}` | Get audit history by actor (paginated) | Admin |
| GET | `/api/v1/admin/audit/type/{entityType}` | Get audit history by type (paginated) | Admin |

## Architecture

### Tax Calculation Flow

1. **Sum Income**: Add all income items → `totalIncome`
2. **Apply Deductions**: Apply deduction rules with caps → `totalDeductions`
3. **Taxable Income**: `taxableIncome = totalIncome - totalDeductions` (min 0)
4. **Progressive Tax**: Apply tax brackets in order → `grossTax`
5. **Apply Credits**: Non-refundable (capped at grossTax) + refundable → `totalCredits`
6. **Tax Withheld**: Sum tax already withheld from income sources
7. **Net Tax**: `netTaxOwing = grossTax - totalCredits - taxWithheld`

Each calculation creates a `CalculationRun` with:
- Input snapshot (filing state at calculation time)
- Bracket breakdown (tax per bracket)
- Credits breakdown (each credit applied)
- Calculation trace (step-by-step explanation)
- Reference to exact rule version used

### Filing State Machine

```
DRAFT → SUBMITTED
         ↓
    (Amendment) → DRAFT → SUBMITTED
```

- **DRAFT**: Can be edited, items added/removed
- **SUBMITTED**: Locked, can only create amendments

### Rule Version States

```
DRAFT → ACTIVE → DEPRECATED
```

- **DRAFT**: Can be modified, not used for calculations
- **ACTIVE**: Used for calculations, only one active per jurisdiction/year
- **DEPRECATED**: No longer active, kept for audit history

## Project Structure

```
src/main/java/com/taxfiling/
├── TaxFilingApplication.java       # Main application entry point
├── config/                         # Configuration classes
├── constants/                      # Application constants
│   └── ApiConstants.java           # Pagination defaults, etc.
├── controller/                     # REST API controllers
│   ├── AuthController.java         # Authentication endpoints
│   ├── TaxFilingController.java    # Filing CRUD operations
│   ├── CalculationController.java  # Tax calculation endpoints
│   ├── SubmissionController.java   # Filing submission
│   ├── TaxRuleController.java      # Admin: tax rule management
│   ├── PublicTaxRuleController.java # Public tax rule lookup
│   └── AuditController.java        # Admin: audit trail viewing
├── service/                        # Business logic layer
│   ├── AuthService.java            # Authentication logic
│   ├── TaxFilingService.java       # Filing management
│   ├── CalculationService.java     # Tax calculation engine
│   ├── SubmissionService.java      # Submission processing
│   ├── TaxRuleService.java         # Tax rule management
│   └── AuditService.java           # Audit trail logging
├── repository/                     # Data access layer (Spring Data JPA)
├── model/                          # JPA entities
│   ├── User.java
│   ├── TaxFiling.java
│   ├── IncomeItem.java
│   ├── DeductionItem.java
│   ├── CreditClaim.java
│   ├── TaxRuleVersion.java
│   ├── TaxBracket.java
│   ├── TaxCreditRule.java
│   ├── DeductionRule.java
│   ├── CalculationRun.java
│   ├── SubmissionRecord.java
│   └── AuditTrail.java
├── model/enums/                    # Enumeration types
│   ├── UserRole.java               # ADMIN, TAXPAYER
│   ├── FilingStatus.java           # DRAFT, READY, SUBMITTED
│   ├── FilingType.java             # ORIGINAL, AMENDMENT
│   ├── IncomeType.java             # EMPLOYMENT, SELF_EMPLOYMENT, etc.
│   ├── DeductionType.java          # RRSP, CHARITABLE, etc.
│   └── RuleStatus.java             # DRAFT, ACTIVE, DEPRECATED
├── dto/                            # Data transfer objects
│   ├── auth/                       # Authentication DTOs
│   ├── filing/                     # Filing DTOs
│   ├── calculation/                # Calculation response DTOs
│   ├── submission/                 # Submission DTOs
│   └── taxrule/                    # Tax rule DTOs
├── mapper/                         # Entity ↔ DTO mappers
├── exception/                      # Custom exceptions
│   ├── ApiException.java           # Standard API exception
│   └── GlobalExceptionHandler.java # @ControllerAdvice handler
├── observability/                  # Observability components
│   ├── CorrelationIdFilter.java    # Request tracing with MDC
│   ├── LoggingAspect.java          # AOP method logging
│   ├── MetricsService.java         # Custom business metrics
│   └── TaxFilingHealthIndicator.java # Custom health check
└── security/                       # Security components
    ├── JwtTokenProvider.java       # JWT generation/validation
    ├── JwtAuthenticationFilter.java # Request filter
    ├── UserPrincipal.java          # Authentication principal
    └── CurrentUser.java            # @CurrentUser annotation

src/main/resources/
├── application.yml                 # Main configuration
├── application-test.yml            # Test profile (H2 database)
├── logback-spring.xml              # Structured logging configuration
└── db/migration/                   # Flyway migrations
    └── V1__initial_schema.sql
```

## Database Schema

### Core Entities

- **users**: User accounts with roles (ADMIN, TAXPAYER)
- **tax_filings**: Tax filing records with status tracking
- **income_items**: Employment, self-employment, investment income
- **deduction_items**: RRSP, charitable donations, etc.
- **credit_claims**: Tax credit claims

### Tax Rules

- **tax_rule_versions**: Versioned tax rules per jurisdiction/year
- **tax_brackets**: Progressive tax brackets
- **tax_credit_rules**: Credit definitions (refundable/non-refundable)
- **deduction_rules**: Deduction limits and rules

### Calculation & Audit

- **calculation_runs**: Calculation history with input snapshots
- **submission_records**: Submission confirmations with filing snapshots
- **audit_trail**: Change history for all entities

## Security

### JWT Tokens

- **Access Token**: 1-hour validity, used for API authentication
- **Refresh Token**: 7-day validity, used to get new access tokens

### Roles

- **TAXPAYER**: Can manage own filings, view tax rules
- **ADMIN**: Full access including tax rule management and audit trails

### Protected Endpoints

- All endpoints except `/api/v1/auth/**` require authentication
- Admin endpoints (`/api/v1/admin/**`) require ADMIN role

## Example Usage

### 1. Register and Login

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "taxpayer@example.com",
    "password": "SecurePass123!",
    "firstName": "John",
    "lastName": "Doe"
  }'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "taxpayer@example.com",
    "password": "SecurePass123!"
  }'
# Returns: { "accessToken": "...", "refreshToken": "...", ... }
```

### 2. Create Filing and Add Income

```bash
# Create filing
curl -X POST http://localhost:8080/api/v1/filings \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "taxYear": 2024,
    "jurisdiction": "CA"
  }'

# Add income item
curl -X POST http://localhost:8080/api/v1/filings/<filing_id>/income-items \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "incomeType": "EMPLOYMENT",
    "source": "Acme Corp",
    "amount": 75000.00,
    "taxWithheld": 15000.00
  }'
```

### 3. Calculate and Submit

```bash
# Calculate tax
curl -X POST http://localhost:8080/api/v1/filings/<filing_id>/calculations \
  -H "Authorization: Bearer <access_token>"

# Submit filing
curl -X POST http://localhost:8080/api/v1/filings/<filing_id>/submit \
  -H "Authorization: Bearer <access_token>"
# Returns confirmation number
```

## Pagination

All list endpoints support pagination with these query parameters:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `page` | 0 | Page number (0-indexed) |
| `size` | 20 | Page size |
| `sort` | varies | Sort field and direction (e.g., `createdAt,desc`) |

Example:
```bash
curl "http://localhost:8080/api/v1/filings?page=0&size=10&sort=createdAt,desc" \
  -H "Authorization: Bearer <access_token>"
```

## Observability

The application includes comprehensive observability features for production monitoring and debugging.

### Health Endpoints

```bash
# Basic health status
curl http://localhost:8080/actuator/health

# Kubernetes probes
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
```

### Metrics & Prometheus

```bash
# List all metrics
curl http://localhost:8080/actuator/metrics

# Prometheus format (for Grafana)
curl http://localhost:8080/actuator/prometheus
```

**Custom Business Metrics:**

| Metric | Type | Description |
|--------|------|-------------|
| `taxfiling.filings.created` | Counter | Total filings created |
| `taxfiling.filings.submitted` | Counter | Total filings submitted |
| `taxfiling.calculations.performed` | Counter | Total tax calculations |
| `taxfiling.calculation.duration` | Timer | Calculation time (p50, p95, p99) |
| `taxfiling.auth.success/failure` | Counter | Authentication attempts |

### Structured JSON Logging

Logs are output in JSON format (ELK/Splunk compatible):

```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "correlationId": "abc123def456",
  "userId": "user-uuid",
  "requestPath": "/api/v1/filings",
  "message": "Filing created successfully"
}
```

### Request Tracing

Every request gets a correlation ID for distributed tracing:

```bash
# Pass your own correlation ID
curl -H "X-Correlation-ID: my-trace-123" http://localhost:8080/api/v1/filings

# Response includes X-Correlation-ID header
# All logs include correlationId field
```

### Runtime Log Level Control

```bash
# View log levels
curl http://localhost:8080/actuator/loggers

# Change at runtime
curl -X POST http://localhost:8080/actuator/loggers/com.taxfiling \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

### Actuator Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health status |
| `/actuator/health/liveness` | K8s liveness probe |
| `/actuator/health/readiness` | K8s readiness probe |
| `/actuator/metrics` | Application metrics |
| `/actuator/prometheus` | Prometheus metrics |
| `/actuator/loggers` | Log level management |

## License

Private - All rights reserved
