-- Input: migration-mysql/V1174__fix_quoted_menu_permissions.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway rollback coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- 回滚 V1174：No-op rollback（V1174 移除了 menu_permissions 中的字面双引号，
-- 属于脏数据清理，不应回滚 — 回滚会重新引入权限校验失效的 bug）。
-- 如果确需回滚，请从备份恢复 menu_permissions 字段。
SELECT 1;
