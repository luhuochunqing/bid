-- Input: migration-mysql/V111__retrospective_review_status_default.sql
-- Output: rollback script for postgres environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- 把默认值回退到 V99 历史值（'PENDING'）。注意：这会与代码 enum 不一致；仅在回退场景下使用。
ALTER TABLE project_retrospective
    MODIFY review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
