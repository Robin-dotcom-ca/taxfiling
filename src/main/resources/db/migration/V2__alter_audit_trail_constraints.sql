-- ===============================================================
-- Tax Filing Backend Service - Alter AuditTrail Action Constraint
-- Version: 2
-- PostgreSQL 16+
-- ===============================================================

ALTER TABLE audit_trail
DROP CONSTRAINT audit_trail_action_check;

ALTER TABLE audit_trail
    ADD CONSTRAINT audit_trail_action_check
        CHECK (action IN (
    'CREATE',
    'UPDATE',
    'DELETE',
    'SUBMISSION',
    'CALCULATION',
    'STATUS_CHANGE'
    ));
