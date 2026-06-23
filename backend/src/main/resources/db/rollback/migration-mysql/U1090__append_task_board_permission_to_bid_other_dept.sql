-- Input: migration-mysql/V1090__append_task_board_permission_to_bid_other_dept.sql
-- Output: rollback note for mysql environments; original custom menu permission order is not stored.
-- Pos: Flyway rollback documentation for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- Data rollback required for UPDATE roles; original values are not stored in migration history.
-- If manual rollback is required, remove only the appended task-board token from bid_other_dept after confirming it was introduced by V1090.
