-- Add partial unique index to enforce only one ACTIVE rule version per jurisdiction and year
-- This prevents race conditions that could occur with application-level enforcement alone

CREATE UNIQUE INDEX uk_one_active_rule_per_jurisdiction_year
    ON tax_rule_versions (jurisdiction, tax_year)
    WHERE status = 'ACTIVE';

COMMENT ON INDEX uk_one_active_rule_per_jurisdiction_year IS
    'Ensures only one tax rule version can be ACTIVE for a given jurisdiction and tax year combination';
