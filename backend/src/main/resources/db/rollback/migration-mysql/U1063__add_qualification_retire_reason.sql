-- Rollback for V1063__add_qualification_retire_reason.sql
-- PR: agent/qoder/qualification-cert-fix
ALTER TABLE business_qualifications DROP COLUMN IF EXISTS retire_reason;
