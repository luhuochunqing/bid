-- ================================================================
-- V1002: Revert form_submission_audit status column to VARCHAR(20)
-- 功能：将 form_submission_audit.status 列从 ENUM 改回 VARCHAR(20)，
--       与 FormSubmissionAuditEntity 的 columnDefinition 保持一致。
-- ================================================================

ALTER TABLE form_submission_audit
MODIFY COLUMN status VARCHAR(20) NOT NULL COMMENT '提交状态：SUCCESS / VALIDATION_FAILED / PROCESSING_ERROR';
