-- V1063: Add retire_reason column to business_qualifications for audit trail
-- PR: agent/qoder/qualification-cert-fix
ALTER TABLE business_qualifications
    ADD COLUMN retire_reason VARCHAR(500) COMMENT '下架原因';
