-- ============================================================
-- V1183: 项目三表单自定义字段扩展 — projects / project_initiation_details 增加 custom_fields JSON 列
-- ============================================================
-- 背景（CO-601 / specs/040-project-form-custom-fields）：
--   项目三表单（project.basic / project.initiation / project.detail）的预置字段锁定不变，
--   管理员可通过表单设计器新增自定义字段；自定义字段值按 scope 分组存入 JSON 列：
--
--   - projects.custom_fields            ← { "project.basic": {...}, "project.detail": {...} }
--   - project_initiation_details.custom_fields ← { "project.initiation": {...} }
--
-- 设计要点：
--   - 一级键为 scope（project.basic / project.detail / project.initiation），
--     二级键为自定义字段 key，值类型不限（string/number/boolean/array）
--   - 预置字段值仍走既有专列（本次不动），custom_fields 仅承载自定义字段
--   - 未知 scope 键由后端 Service 过滤丢弃（log.warn），列级不做约束
--   - NULL = 老数据/无自定义字段，读取侧降级为空 Map（契约 §3）
--
-- 影响范围：
--   - 仅新增两个可空 JSON 列，无存量数据迁移、无索引变更
--   - MySQL 8.0 JSON 类型（与 customer_info_json 等既有 JSON 列一致）
--
-- 幂等性：ADD COLUMN 非幂等（重复执行报 1060 列已存在），由 Flyway 版本管理保证只执行一次
-- ============================================================

ALTER TABLE projects
    ADD COLUMN custom_fields JSON NULL COMMENT 'CO-601 自定义字段（按 scope 分组：project.basic / project.detail）';

ALTER TABLE project_initiation_details
    ADD COLUMN custom_fields JSON NULL COMMENT 'CO-601 自定义字段（按 scope 分组：project.initiation）';
