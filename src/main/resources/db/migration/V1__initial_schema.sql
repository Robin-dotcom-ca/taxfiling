-- =====================================================
-- Tax Filing Backend Service - Initial Schema
-- Version: 1
-- PostgreSQL 16+
-- =====================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =====================================================
-- USERS & AUTHENTICATION
-- =====================================================
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'TAXPAYER')),
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- =====================================================
-- TAX RULES & VERSIONING
-- =====================================================
CREATE TABLE tax_rule_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    jurisdiction    VARCHAR(50) NOT NULL,
    tax_year        INTEGER NOT NULL,
    version         INTEGER NOT NULL DEFAULT 1,
    status          VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'DEPRECATED')),
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_tax_rules_jurisdiction_year_version
        UNIQUE (jurisdiction, tax_year, version)
);

CREATE INDEX idx_tax_rules_jurisdiction_year ON tax_rule_versions(jurisdiction, tax_year);
CREATE INDEX idx_tax_rules_status ON tax_rule_versions(status);

-- =====================================================
-- PROGRESSIVE TAX BRACKETS
-- =====================================================
CREATE TABLE tax_brackets (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_version_id     UUID NOT NULL REFERENCES tax_rule_versions(id) ON DELETE CASCADE,
    min_income          DECIMAL(15,2) NOT NULL,
    max_income          DECIMAL(15,2),
    rate                DECIMAL(5,4) NOT NULL,
    bracket_order       INTEGER NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_brackets_rule_version_order UNIQUE (rule_version_id, bracket_order),
    CONSTRAINT chk_brackets_income_range CHECK (max_income IS NULL OR max_income > min_income),
    CONSTRAINT chk_brackets_rate_range CHECK (rate >= 0 AND rate <= 1)
);

CREATE INDEX idx_brackets_rule_version ON tax_brackets(rule_version_id);

-- =====================================================
-- TAX CREDIT RULES
-- =====================================================
CREATE TABLE tax_credit_rules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_version_id     UUID NOT NULL REFERENCES tax_rule_versions(id) ON DELETE CASCADE,
    credit_type         VARCHAR(50) NOT NULL,
    name                VARCHAR(100) NOT NULL,
    amount              DECIMAL(15,2) NOT NULL,
    is_refundable       BOOLEAN NOT NULL DEFAULT FALSE,
    eligibility_rules   JSONB,
    max_amount          DECIMAL(15,2),
    phase_out_start     DECIMAL(15,2),
    phase_out_rate      DECIMAL(5,4),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_credit_rules_rule_version ON tax_credit_rules(rule_version_id);
CREATE INDEX idx_credit_rules_type ON tax_credit_rules(credit_type);

-- =====================================================
-- DEDUCTION RULES
-- =====================================================
CREATE TABLE deduction_rules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_version_id     UUID NOT NULL REFERENCES tax_rule_versions(id) ON DELETE CASCADE,
    deduction_type      VARCHAR(50) NOT NULL,
    name                VARCHAR(100) NOT NULL,
    max_amount          DECIMAL(15,2),
    max_percentage      DECIMAL(5,4),
    eligibility_rules   JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_deduction_rules_rule_version ON deduction_rules(rule_version_id);
CREATE INDEX idx_deduction_rules_type ON deduction_rules(deduction_type);

-- =====================================================
-- TAX FILINGS
-- =====================================================
CREATE TABLE tax_filings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    tax_year            INTEGER NOT NULL,
    jurisdiction        VARCHAR(50) NOT NULL,
    status              VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'READY', 'SUBMITTED')),
    filing_type         VARCHAR(30) NOT NULL DEFAULT 'ORIGINAL' CHECK (filing_type IN ('ORIGINAL', 'AMENDMENT')),
    original_filing_id  UUID REFERENCES tax_filings(id),
    metadata            JSONB NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- CREATE UNIQUE INDEX idx_filings_unique_original
--     ON tax_filings(user_id, tax_year, jurisdiction)
--     WHERE filing_type = 'ORIGINAL';

CREATE INDEX idx_filings_user ON tax_filings(user_id);
CREATE INDEX idx_filings_status ON tax_filings(status);
CREATE INDEX idx_filings_year ON tax_filings(tax_year);
CREATE INDEX idx_filings_jurisdiction ON tax_filings(jurisdiction);

-- =====================================================
-- INCOME ITEMS
-- =====================================================
CREATE TABLE income_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filing_id       UUID NOT NULL REFERENCES tax_filings(id) ON DELETE CASCADE,
    income_type     VARCHAR(50) NOT NULL CHECK (income_type IN (
        'EMPLOYMENT', 'SELF_EMPLOYMENT', 'INVESTMENT', 'RENTAL',
        'CAPITAL_GAINS', 'PENSION', 'EI_BENEFITS', 'OTHER'
    )),
    source          VARCHAR(255),
    amount          DECIMAL(15,2) NOT NULL,
    tax_withheld    DECIMAL(15,2) NOT NULL DEFAULT 0,
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_income_amount_positive CHECK (amount >= 0),
    CONSTRAINT chk_income_withheld_positive CHECK (tax_withheld >= 0)
);

CREATE INDEX idx_income_filing ON income_items(filing_id);
CREATE INDEX idx_income_type ON income_items(income_type);

-- =====================================================
-- DEDUCTION ITEMS
-- =====================================================
CREATE TABLE deduction_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filing_id       UUID NOT NULL REFERENCES tax_filings(id) ON DELETE CASCADE,
    deduction_type  VARCHAR(50) NOT NULL CHECK (deduction_type IN (
        'RRSP', 'UNION_DUES', 'CHILDCARE', 'MOVING',
        'CHARITABLE', 'MEDICAL', 'STUDENT_LOAN_INTEREST', 'HOME_OFFICE', 'OTHER'
    )),
    description     VARCHAR(255),
    amount          DECIMAL(15,2) NOT NULL,
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_deduction_amount_positive CHECK (amount >= 0)
);

CREATE INDEX idx_deduction_filing ON deduction_items(filing_id);
CREATE INDEX idx_deduction_type ON deduction_items(deduction_type);

-- =====================================================
-- CREDIT CLAIMS
-- =====================================================
CREATE TABLE credit_claims (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filing_id       UUID NOT NULL REFERENCES tax_filings(id) ON DELETE CASCADE,
    credit_type     VARCHAR(50) NOT NULL,
    claimed_amount  DECIMAL(15,2) NOT NULL,
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_credit_amount_positive CHECK (claimed_amount >= 0)
);

CREATE INDEX idx_credits_filing ON credit_claims(filing_id);
CREATE INDEX idx_credits_type ON credit_claims(credit_type);

-- =====================================================
-- CALCULATION RUNS
-- =====================================================
CREATE TABLE calculation_runs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filing_id           UUID NOT NULL REFERENCES tax_filings(id),
    rule_version_id     UUID NOT NULL REFERENCES tax_rule_versions(id),
    total_income        DECIMAL(15,2) NOT NULL,
    total_deductions    DECIMAL(15,2) NOT NULL,
    taxable_income      DECIMAL(15,2) NOT NULL,
    gross_tax           DECIMAL(15,2) NOT NULL,
    total_credits       DECIMAL(15,2) NOT NULL,
    tax_withheld        DECIMAL(15,2) NOT NULL,
    net_tax_owing       DECIMAL(15,2) NOT NULL,
    bracket_breakdown   JSONB NOT NULL,
    credits_breakdown   JSONB NOT NULL,
    calculation_trace   JSONB,
    input_snapshot      JSONB NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_calc_filing ON calculation_runs(filing_id);
CREATE INDEX idx_calc_rule_version ON calculation_runs(rule_version_id);
CREATE INDEX idx_calc_created ON calculation_runs(created_at DESC);

-- =====================================================
-- SUBMISSION RECORDS
-- =====================================================
CREATE TABLE submission_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filing_id           UUID NOT NULL REFERENCES tax_filings(id),
    calculation_run_id  UUID NOT NULL REFERENCES calculation_runs(id),
    confirmation_number VARCHAR(50) NOT NULL,
    submitted_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    submitted_by        UUID NOT NULL REFERENCES users(id),
    filing_snapshot     JSONB NOT NULL,
    ip_address          VARCHAR(45),
    user_agent          VARCHAR(500),

    CONSTRAINT uk_submission_confirmation UNIQUE (confirmation_number),
    CONSTRAINT uk_submission_filing UNIQUE (filing_id)
);

CREATE INDEX idx_submission_filing ON submission_records(filing_id);
CREATE INDEX idx_submission_confirmation ON submission_records(confirmation_number);

-- =====================================================
-- AUDIT TRAIL
-- =====================================================
CREATE TABLE audit_trail (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    action          VARCHAR(20) NOT NULL
        CONSTRAINT audit_trail_action_check
        CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    actor_id        UUID REFERENCES users(id),
    old_values      JSONB,
    new_values      JSONB,
    changed_fields  JSONB,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity ON audit_trail(entity_type, entity_id);
CREATE INDEX idx_audit_actor ON audit_trail(actor_id);
CREATE INDEX idx_audit_created ON audit_trail(created_at DESC);
CREATE INDEX idx_audit_entity_created ON audit_trail(entity_type, entity_id, created_at DESC);

-- =====================================================
-- TRIGGER FUNCTION: Auto-update updated_at
-- =====================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply triggers
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_tax_rule_versions_updated_at BEFORE UPDATE ON tax_rule_versions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_tax_brackets_updated_at BEFORE UPDATE ON tax_brackets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_tax_credit_rules_updated_at BEFORE UPDATE ON tax_credit_rules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_deduction_rules_updated_at BEFORE UPDATE ON deduction_rules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_tax_filings_updated_at BEFORE UPDATE ON tax_filings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_income_items_updated_at BEFORE UPDATE ON income_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_deduction_items_updated_at BEFORE UPDATE ON deduction_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_credit_claims_updated_at BEFORE UPDATE ON credit_claims
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
