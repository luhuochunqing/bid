-- PR: #TODO
-- Rollback remark fields from personnel and personnel_certificate
ALTER TABLE personnel DROP COLUMN IF EXISTS remark;

ALTER TABLE personnel_certificate DROP COLUMN IF EXISTS remark;