-- ============================================================
-- U1183: 回滚 V1183 - 删除 projects / project_initiation_details 的 custom_fields 列
-- ============================================================
-- Input: V1183__add_custom_fields_to_project_tables.sql
--
-- ⚠️ 数据丢失警告：
--   回滚会 DROP 两列，已存储的自定义字段值将永久丢失且不可恢复。
--   本脚本仅供灾备使用，执行前必须先备份：
--     SELECT id, custom_fields FROM projects WHERE custom_fields IS NOT NULL;
--     SELECT id, custom_fields FROM project_initiation_details WHERE custom_fields IS NOT NULL;
--
-- 幂等性：DROP COLUMN 非幂等（重复执行报 1091 列不存在），仅手动单次执行
-- ============================================================

ALTER TABLE projects
    DROP COLUMN custom_fields;

ALTER TABLE project_initiation_details
    DROP COLUMN custom_fields;
