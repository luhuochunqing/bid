-- Input: migration-mysql/V1197__fix_compliance_check_status_enum.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- Rollback for V1197: revert compliance_check_results.status back to VARCHAR(32).
-- Safe reversion: only needed if rolling back the V1196/V1197 quality-check extension.
-- Note: after rollback, the JPA entity (TaskStatus @Enumerated(EnumType.STRING)) will fail
-- Hibernate schema-validation — pair this rollback with the pre-V1196 jar.

ALTER TABLE compliance_check_results
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED' COMMENT '任务状态: INIT, PARSING, CHECKING, COMPLETED, FAILED';
