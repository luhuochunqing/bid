-- Input: migration-mysql/V1171__backfill_archive_files_for_obs_direct_uploads.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway rollback coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- 回滚 V1171：删除由 V1171 插入的 archive_file 记录
-- 通过 file_path 前缀 'obs-direct:' 限定（V1171 只回填 obs-direct: 前缀的文档）
-- 注：multipart 归档的 file_path 不会以 obs-direct: 开头，无误删风险
-- 注：V1171 之后新上传的 OBS 直传文档也会以 obs-direct: 开头，回滚时会一并删除
-- Manual rollback required: created_at 时间范围是启发式，生产环境回滚需手动确认 V1171 执行时间后调整 INTERVAL
DELETE FROM archive_file
WHERE file_path LIKE 'obs-direct:%'
  AND created_at >= NOW() - INTERVAL 1 HOUR;
