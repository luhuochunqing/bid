-- V1148: CO-537 回填存量项目的"项目部门"和"项目负责人部门"字段
-- 背景：ProjectTransferService.transfer() 历史版本在项目转移时遗漏了部门回填，
--   导致存量数据中 tenders.department 和 project_initiation_details.leader_department
--   存在大量空值。本次修复已让新转移的项目正确回填，此迁移补齐存量数据。
--
-- 回填策略：
--   1. tenders.department：从 project_manager_id 关联 users.department_name 回填
--   2. project_initiation_details.leader_department：从 project.project 的 manager_id
--      关联 users.department_name 回填（与 ProjectTransferService 的回填源一致）
--
-- 安全性：
--   - 仅更新 department/leader_department 为 NULL 或空字符串的记录，已有值不覆盖
--   - 幂等：重复执行无副作用
--   - 不修改表结构，仅数据回填
--
-- 关联代码：
--   - ProjectTransferService.java L131/L148（CO-537 新增的 setter 调用）
--   - User.departmentName 字段（department_name 列）
--
-- 事务：使用显式事务保证原子性

START TRANSACTION;

-- 1. 回填 tenders.department（标讯中心"项目部门"字段）
UPDATE tenders t
    JOIN users u ON t.project_manager_id = u.id
SET t.department = u.department_name
WHERE t.project_manager_id IS NOT NULL
  AND u.department_name IS NOT NULL
  AND u.department_name <> ''
  AND (t.department IS NULL OR t.department = '');

-- 2. 回填 project_initiation_details.leader_department（投标项目"项目负责人部门"字段）
--    数据源：projects.manager_id（项目负责人），与 ProjectTransferService 的 newOwner 对齐
UPDATE project_initiation_details d
    JOIN projects p ON d.project_id = p.id
    JOIN users u ON p.manager_id = u.id
SET d.leader_department = u.department_name
WHERE p.manager_id IS NOT NULL
  AND u.department_name IS NOT NULL
  AND u.department_name <> ''
  AND (d.leader_department IS NULL OR d.leader_department = '');

COMMIT;
