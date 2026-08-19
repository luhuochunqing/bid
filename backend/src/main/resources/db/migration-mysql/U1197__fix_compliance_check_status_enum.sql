-- Rollback for V1197: revert compliance_check_results.status back to VARCHAR(32)
-- Safe reversion: only needed if rolling back the V1196/V1197 quality-check extension.
-- Note: after rollback, the JPA entity (TaskStatus @Enumerated(EnumType.STRING)) will fail
-- Hibernate schema-validation — pair this rollback with the pre-V1196 jar.

ALTER TABLE compliance_check_results
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED' COMMENT '任务状态: INIT, PARSING, CHECKING, COMPLETED, FAILED';
