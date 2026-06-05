-- Input: migration-mysql/V132__tender_assignment_record_type.sql
-- Output: rollback script for mysql environments
-- Rollback V132: 删除 type 列

ALTER TABLE tender_assignment_records DROP COLUMN type;
