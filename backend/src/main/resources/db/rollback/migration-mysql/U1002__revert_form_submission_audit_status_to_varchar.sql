-- Input: V1002__revert_form_submission_audit_status_to_varchar.sql
-- 回滚：将 form_submission_audit status 从 VARCHAR 改回 ENUM
-- Flyway rollback MySQL 8.0

ALTER TABLE form_submission_audit
MODIFY COLUMN status ENUM('success', 'validation_failed', 'processing_error') NOT NULL COMMENT '提交状态';
