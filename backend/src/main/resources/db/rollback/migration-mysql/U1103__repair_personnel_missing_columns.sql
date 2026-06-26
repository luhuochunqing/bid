-- Input: V1103__repair_personnel_missing_columns.sql
-- Rollback for V1103__repair_personnel_missing_columns.sql
--
-- Manual rollback required.
--
-- V1103 is a SCHEMA-REPAIR migration: it re-adds columns that V1065/V1066
-- declared in flyway_schema_history (success=1) but that never materialized in
-- the live DB (the DB was restored from an older backup after those versions
-- ran). The columns are REQUIRED by PersonnelCertificateEntity /
-- PersonnelEducationEntity, so dropping them re-introduces the CO-362 500
-- (Unknown column 'is_permanent' / 'title' / 'remark' / 'is_highest_education_school').
--
-- Do NOT run this rollback in isolation. It must only be executed together
-- with rolling back V1065/V1066 AND the JPA entities that depend on these
-- columns. On a healthy DB these columns are load-bearing, not optional.
--
-- Reverse DDL (execute only after the above review):

ALTER TABLE personnel_certificate
    DROP COLUMN title,
    DROP COLUMN is_permanent,
    DROP COLUMN remark;

ALTER TABLE personnel_education
    DROP COLUMN is_highest_education_school;
