-- Input: V1001__alter_form_submission_audit_status_enum.sql
-- Description: Revert form_submission_audit status column back to VARCHAR(20)
ALTER TABLE form_submission_audit
MODIFY COLUMN status VARCHAR(20) NOT NULL COMMENT '提交状态：SUCCESS / VALIDATION_FAILED / PROCESSING_ERROR';
