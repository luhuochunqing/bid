-- Input: V1013__qualification_blueprint_fields.sql
-- Rollback for V1013__qualification_blueprint_fields.sql

DROP INDEX idx_cert_no_unique ON business_qualifications;

ALTER TABLE business_qualifications
    DROP COLUMN level,
    DROP COLUMN agency,
    DROP COLUMN agency_contact,
    DROP COLUMN cert_scope,
    DROP COLUMN cert_review_note;
