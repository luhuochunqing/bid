-- 回滚 V1174__fix_quoted_menu_permissions.sql
-- 注意：回滚无法自动恢复"双引号包裹"的脏数据状态（也无此必要）。
-- 本回滚为 no-op：因 V1174 是数据修复脚本，原脏数据状态是 bug，不应恢复。
-- 若确需恢复，请从备份中恢复 roles 表。

SELECT 1;
