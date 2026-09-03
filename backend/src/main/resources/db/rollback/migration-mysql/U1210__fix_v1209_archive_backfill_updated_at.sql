-- Input: migration-mysql/V1210__fix_v1209_archive_backfill_updated_at.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- U1210: 回滚 fix_v1209_archive_backfill_updated_at
-- 说明: 本迁移是历史数据补充，回滚仅针对执行窗口内（1小时内）由 V1210 生成的记录。
-- Manual rollback required: 逆序回滚：先删除派生的 archive_file 记录，再删除无其他附件依赖的补全新建 project_archive 记录。
-- 注意: 与 U1209 逻辑一致；project_name 使用了 LEFT(p.name,255) 截断，故回滚删除仍以 id/时间窗口为准，
--       不依赖 project_name 精确匹配。

DELETE FROM archive_file
WHERE file_size = 0
  AND created_at >= NOW() - INTERVAL 1 HOUR;

DELETE FROM project_archive
WHERE archive_status = 'ACTIVE'
  AND id NOT IN (SELECT DISTINCT archive_id FROM archive_file)
  AND created_at >= NOW() - INTERVAL 1 HOUR;
