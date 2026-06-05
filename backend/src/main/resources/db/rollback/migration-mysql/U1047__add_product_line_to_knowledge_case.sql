-- Input: migration-mysql/V1047__add_product_line_to_knowledge_case.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- U1047: 回滚 knowledge_case.product_line 列
ALTER TABLE knowledge_case DROP COLUMN IF EXISTS product_line;
