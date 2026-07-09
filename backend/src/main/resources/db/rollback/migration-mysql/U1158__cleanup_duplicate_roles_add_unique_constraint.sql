-- Input: V1158__cleanup_duplicate_roles_add_unique_constraint.sql
-- Rollback for V1158__cleanup_duplicate_roles_add_unique_constraint.sql
-- U1158: 回滚 V1158 — 删除 roles.code 唯一约束
-- 注意：回滚仅删除唯一约束，不会恢复已删除的重复角色行（重复数据本身就是 bug）。
--       如需恢复重复行，需从备份恢复。

ALTER TABLE roles DROP INDEX uk_roles_code;
