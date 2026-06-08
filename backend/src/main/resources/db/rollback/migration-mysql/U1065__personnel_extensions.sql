-- Rollback for V1065__personnel_extensions.sql
-- PR: agent/trae/personnel-cert

DROP TABLE IF EXISTS personnel_operation_log;

ALTER TABLE personnel_education DROP COLUMN IF EXISTS is_highest_education_school;

ALTER TABLE personnel_certificate DROP COLUMN IF EXISTS is_permanent;
ALTER TABLE personnel_certificate DROP COLUMN IF EXISTS title;

ALTER TABLE personnel DROP COLUMN IF EXISTS birth_date;
