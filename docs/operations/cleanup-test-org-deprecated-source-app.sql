-- ============================================================================
-- 清理测试环境 organization_departments 中的过期 ehsy 来源重复数据
-- ============================================================================
-- 背景/根因（已实测确认，2026-08-25）：
--   organization_departments 表中存在两个来源：ehsy 与 oss。
--   - ehsy：历史目录同步来源，最后同步 2026-06-15（已停更 2 个月）
--   - oss ：当前有效同步来源，最后同步 2026-08-04（活跃）
--   同一部门（external_dept_id / department_code 相同）在两根下各存在一条，
--   前端默认不传 sourceApp 按全部来源返回，导致 /settings/organization 同名重复。
--   根因：早期用 ehsy，后切 oss，ehsy 的 2514 条历史数据从未清理。
--   生产环境未启用 ehsy 来源，因此无此问题。
--
-- 目标环境：⚠️ 仅测试环境 172.16.38.78 (winbid)，禁止在任意其他环境执行
-- 影响范围：删除 source_app='ehsy' 全部记录（本次 2514 条）。
--   安全检查（已实测）：
--     - 无外键引用 organization_departments → 0 行信息_schema 关联
--     - users.department_code 存的是工号数字标识，与该表 department_code 无耦合
--     - ehsy 与 oss 在 external_dept_id/department_code 上完全重复，删 ehsy 不丢唯一部门
--
-- 回滚：备份表 organization_departments_backup_ehsy 已保存 ehys 全部行，
--   如需回滚：INSERT INTO organization_departments SELECT * FROM organization_departments_backup_ehsy;
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 第 0 步 环境安全校验：确认当前库 & 数据分布（应与实测一致）
-- ----------------------------------------------------------------------------
SELECT source_app, COUNT(*) total,
       MAX(last_synced_at) last_synced_ts
FROM organization_departments
GROUP BY source_app;

-- ----------------------------------------------------------------------------
-- 第 1 步 备份：将待删除的 ehsy 行复制到备份表，便于回滚
--   注：已在本会话提前执行，若备份表已存在可跳过或 DROP 后重建
-- ----------------------------------------------------------------------------
-- DROP TABLE IF EXISTS organization_departments_backup_ehsy;
-- CREATE TABLE organization_departments_backup_ehsy LIKE organization_departments;
-- INSERT INTO organization_departments_backup_ehsy
--   SELECT * FROM organization_departments WHERE source_app='ehsy';
-- 备份行数核对（应=2514）
-- SELECT 'BACKUP_ROWS' AS step, COUNT(*) AS row_count
-- FROM organization_departments_backup_ehsy;

-- ----------------------------------------------------------------------------
-- 第 2 步 预检（DRY-RUN）：删除前核对目标行数与来源关系
-- ----------------------------------------------------------------------------
SELECT 'EHYS_RUNS' AS step, COUNT(*) AS row_count
FROM organization_departments WHERE source_app='ehsy';
SELECT 'EHYS_ONLY_DEPS' AS step, COUNT(*) AS row_count
FROM organization_departments e
WHERE e.source_app='ehsy'
  AND e.department_code NOT IN
      (SELECT o.department_code FROM organization_departments o WHERE o.source_app='oss');
-- ↑ 若 EHYS_ONLY_DEPS>0 表示存在 ehsy 独有的部门（oss 没有），删除会丢部门，需人工复核。

-- ----------------------------------------------------------------------------
-- 第 3 步 实际删除：确认预检无误后取消注释执行
-- ----------------------------------------------------------------------------
/*
DELETE FROM organization_departments WHERE source_app='ehsy';
*/
-- 删除后核对：应只剩 oss（+备份表）
-- SELECT source_app, COUNT(*) AS row_count FROM organization_departments GROUP BY source_app;