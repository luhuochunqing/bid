-- Input: V1059__performance_audit_user_fields.sql
-- Rollback for V1059__performance_audit_user_fields.sql

ALTER TABLE performance_record
    DROP COLUMN updated_by,
    MODIFY COLUMN created_by BIGINT COMMENT '创建人ID';
