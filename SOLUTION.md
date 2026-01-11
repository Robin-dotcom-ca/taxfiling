# Solution Documentation

This document outlines the architecture decisions, tradeoffs, AI usage, and production readiness considerations for the Tax Filing Backend Service.

## Table of Contents

1. [Architecture Decisions](#architecture-decisions)
2. [AI Usage](#ai-usage)
3. [Design Tradeoffs](#design-tradeoffs)
4. [Production Readiness Gaps](#production-readiness-gaps)
5. [API Testing](#api-testing)

---

## Architecture Decisions

### 1. Layered Architecture

**Decision**: Standard Spring Boot layered architecture (Controller → Service → Repository).

**Rationale**:
- Clear separation of concerns
- Easy to test each layer independently
- Familiar pattern for Java developers
- Supports dependency injection and loose coupling

**Structure**:
```
Controller (REST API) → Service (Business Logic) → Repository (Data Access)
     ↓                        ↓                          ↓
    DTOs                   Entities                  Database
```

### 2. Tax Rule Versioning System

**Decision**: Implemented immutable, versioned tax rules with state machine (DRAFT → ACTIVE → DEPRECATED).

**Rationale**:
- Tax rules change annually or even more frequently; historical calculations must remain reproducible
- Only one active rule per jurisdiction/year prevents ambiguity
- DRAFT state allows admins to prepare rules before activation
- DEPRECATED rules are retained for audit history
- Each calculation references the exact rule version used

**Implementation**:
```java
// Only one active rule per jurisdiction/year
@Query("SELECT t FROM TaxRuleVersion t WHERE t.jurisdiction = :jurisdiction " +
       "AND t.taxYear = :taxYear AND t.status = 'ACTIVE'")
Optional<TaxRuleVersion> findActiveRule(String jurisdiction, Integer taxYear);
```

### 3. Calculation Traceability (Advanced Feature - Option A: Rule Trace / Explainability)

**Decision**: Store complete calculation history with input snapshots and step-by-step breakdowns.

**Rationale**:
- Tax calculations must be auditable and explainable
- Users need to understand how their tax was computed
- Supports "what-if" scenarios by comparing calculation runs
- Input snapshots capture filing state at calculation time

**Stored Data**:
- `inputSnapshot`: Complete filing state (JSONB)
- `bracketBreakdown`: Tax computed per bracket
- `creditsBreakdown`: Each credit applied
- `calculationTrace`: Human-readable step-by-step explanation
- `ruleVersionId`: Exact rules used

### 4. JSONB for Flexible Metadata

**Decision**: Use PostgreSQL JSONB columns for metadata fields (T4 box details, eligibility rules, calculation breakdowns).

**Rationale**:
- Tax forms have many optional fields that vary by type
- Schema can evolve without migrations
- Efficient querying with GIN indexes if needed
- Preserves flexibility for future income/deduction types

**Examples**:
- `income_items.metadata`: T4 slip box numbers, employer details
- `tax_credit_rules.eligibility_rules`: Dynamic eligibility criteria
- `calculation_runs.input_snapshot`: Complete filing state

### 5. JWT Authentication with Refresh Tokens

**Decision**: Stateless JWT authentication with short-lived access tokens and long-lived refresh tokens.

**Rationale**:
- Stateless: No server-side session storage needed
- Short access token (1 hour) limits exposure if compromised
- Refresh tokens (7 days) provide good UX without frequent logins
- Scales horizontally without session synchronization

**Token Flow**:
```
Login → Access Token (1h) + Refresh Token (7d)
         ↓
API Request with Access Token
         ↓
Token Expired → Use Refresh Token → New Access Token
```

### 6. Amendment Model

**Decision**: Amendments are new filings linked to original via `originalFilingId`.

**Rationale**:
- Preserves original filing history unchanged
- Each amendment is independently calculable
- Supports multiple amendments (amendment of amendment)
- Clear audit trail of changes over time


### 7. Audit Trail Design

**Decision**: Separate `audit_trail` table with before/after snapshots stored as JSONB.

**Rationale**:
- Complete change history for compliance
- Decoupled from business entities (no pollution of domain models)
- Supports any entity type via `entityType` + `entityId`
- JSONB allows flexible before/after comparisons

**Logged Actions**:
- CREATE, UPDATE, DELETE for all entities
- STATUS_CHANGE for state transitions
- SUBMISSION for filing submissions
- CALCULATION for tax calculations

### 8. JPA Collection Strategy (Set vs List)

**Decision**: Use `Set` instead of `List` for `@OneToMany` collections in `TaxFiling` entity.

**Rationale**:
- Hibernate throws `MultipleBagFetchException` when fetching multiple `List` (bag) collections simultaneously
- `Set` collections avoid this issue and are semantically correct for entity relationships (no duplicates)
- Required proper `equals()` and `hashCode()` implementation in `BaseEntity`

**Implementation**:
```java
// BaseEntity - consistent hashCode for JPA entities
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BaseEntity that = (BaseEntity) o;
    return id != null && Objects.equals(id, that.id);
}

@Override
public int hashCode() {
    return getClass().hashCode(); // Constant for new entities
}
```

### 9. Minimal Repository Pattern

**Decision**: Keep repository interfaces minimal with only methods that are actually used.

**Rationale**:
- Reduces maintenance burden and cognitive load
- Spring Data JPA provides standard CRUD operations via `JpaRepository`
- Custom query methods should only be added when needed
- Unused methods were removed during code cleanup

---

## AI Usage

### Development Process

This project was developed with assistance from Claude (Anthropic's AI assistant). Here's how AI was used:

### 1. Code Generation

**What AI Generated**:
- Entity classes with JPA annotations
- Repository interfaces with custom queries
- Service layer business logic
- REST controllers with OpenAPI annotations
- DTOs and mappers
- Unit tests with Mockito
- Database migrations (Flyway SQL)
- Docker configuration

**Human Oversight**:
- All generated code was reviewed for correctness
- Security-sensitive code (JWT, password hashing) was verified
- Business logic alignment with requirements was validated
- All compilation errors were fixed

### 2. Architecture Guidance

**AI Contributions**:
- Suggested layered architecture pattern
- Recommended JSONB for flexible metadata
- Proposed calculation traceability design

**Human Decisions**:
- Final architecture choices
- Technology stack selection
- Security requirements
- API design conventions
- Pagination implementation

### 3. Documentation

**AI Generated**:
- README.md with setup instructions
- API endpoint documentation
- Code comments and Javadoc
- Postman collection with example request bodies for all endpoints
- SOLUTION.md architecture documentation

### 4. Testing Strategy

**AI Contributions**:
- Test structure with nested classes
- Mock setup patterns
- Edge case identification
- AssertJ assertion patterns


---

## Design Tradeoffs

### 1. Simplicity vs. Completeness

| Aspect | Chosen Approach | Alternative | Tradeoff |
|--------|----------------|-------------|----------|
| Tax brackets | Single federal jurisdiction | Multi-jurisdiction (federal + provincial) | Simpler model, but real Canadian taxes need provincial rates |
| Income types | Enum-based | Dynamic/configurable | Faster development, but adding types requires code changes |
| Deduction rules | Basic caps | Complex eligibility rules engine | Covers common cases, but lacks flexibility for edge cases |

### 2. Performance vs. Simplicity

| Aspect | Chosen Approach | Alternative | Tradeoff |
|--------|----------------|-------------|----------|
| Calculation history | Store complete snapshots | Store only deltas | More storage, but simpler querying and reconstruction |
| Eager loading | Selective `@EntityGraph` | Always lazy | Better performance for known queries, but requires careful management |
| Pagination | Spring Data Pageable | Cursor-based | Simpler implementation, but less efficient for deep pages |

### 3. Security vs. Convenience

| Aspect | Chosen Approach | Alternative | Tradeoff |
|--------|----------------|-------------|----------|
| Token expiry | 1 hour access / 7 day refresh | Longer-lived tokens | More secure, but requires refresh logic |
| Password policy | Basic validation | Complex rules | Simpler UX, but potentially weaker passwords |
| Rate limiting | Not implemented | Bucket4j or similar | Development speed, but vulnerable to abuse |

### 4. Flexibility vs. Type Safety

| Aspect | Chosen Approach | Alternative | Tradeoff |
|--------|----------------|-------------|----------|
| Metadata fields | JSONB | Separate normalized tables | Flexible schema, but loses compile-time type checking |
| Credit types | String identifier | Enum | Allows dynamic credits, but no compile-time validation |
| Eligibility rules | JSONB | DSL/Rule engine | Simple storage, but limited expressiveness |

---

## Production Readiness Gaps

### Critical (Must Fix Before Production)

#### 1. Security Hardening

#### 2. Authentication Improvements

#### 3. Data Protection

### High Priority (Should Fix)

#### 4. Observability

#### 5. Resilience

#### 6. Performance

### Medium Priority (Nice to Have)

#### 7. Operational

#### 8. Testing

#### 9. API Improvements


---


## API Testing

### Postman Collection

A comprehensive Postman collection is available at `postman/Tax_Filing_API.postman_collection.json` with:

**Features**:
- All API endpoints organized by category
- Example request bodies with realistic Canadian tax data
- Automatic token extraction from login responses
- Collection variables for dynamic IDs and tokens

**Usage**:
1. Import collection into Postman
2. Set `baseUrl` variable (default: `http://localhost:8080`)
3. Login to automatically capture `accessToken`
4. Use other endpoints with auto-populated authorization

---

## Conclusion

This solution provides a solid foundation for a tax filing system with:

- **Strengths**: Clean architecture, full audit trail, calculation transparency, versioned rules, comprehensive observability
- **Limitations**: Single jurisdiction, basic deduction rules, no document handling
- **Production Gaps**: Security hardening, resilience patterns needed
- **Testing**: Postman collection with realistic examples for all endpoints

The codebase is well-structured for future enhancements and follows Spring Boot best practices. With the identified production readiness improvements, it can be evolved into a production-grade system.

---

*Document Version: 1.1*
*Last Updated: January 2025*
