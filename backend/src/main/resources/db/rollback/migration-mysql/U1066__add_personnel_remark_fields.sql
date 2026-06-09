-- U1066__add_personnel_remark_fields.sql
-- Input: V1066__add_personnel_remark_fields.sql
-- Rollback for V1066__add_personnel_remark_fields.sql
-- PR: #TODO
-- Rollback remark fields from personnel and personnel_certificate
ALTER TABLE personnel DROP COLUMN IF EXISTS remark;

ALTER TABLE personnel_certificate DROP COLUMN IF EXISTS remark;
