-- Input: migration-mysql/V1125__fix_co_439_admin_staff_navigation_permissions.sql
-- Output: rollback note for mysql environments; original custom menu permission order is not stored.
-- Pos: Flyway rollback documentation for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- Data rollback required for UPDATE roles; original values are not stored in migration history.
-- If manual rollback is required, remove the appended 'knowledge' and 'knowledge-qualification' tokens
-- from bid-administration role's menu_permissions after confirming they were introduced by V1125.
