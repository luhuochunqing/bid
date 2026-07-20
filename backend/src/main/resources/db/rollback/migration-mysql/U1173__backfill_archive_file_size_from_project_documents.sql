-- Input: migration-mysql/V1173__backfill_archive_file_size_from_project_documents.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway rollback coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- 回滚 V1173：把由 V1173 修改的 archive_file.file_size 恢复为 0
-- 注：V1173 仅修改 file_size = 0 的记录，回滚后这些记录恢复为 0
-- 注：无法区分哪些记录是 V1173 修改过的（V1173 没有 created_at 时间窗口），因此回滚会把所有
--     file_size > 0 且 file_path 对应 project_documents.file_url 的记录恢复为 0
--     这会同时影响 multipart 路径正常归档的真实字节数据，属于有损回滚
-- Manual rollback required: 生产环境回滚前请先备份 archive_file 表，并在测试环境验证
UPDATE archive_file af
INNER JOIN project_documents pd ON pd.file_url = af.file_path
SET af.file_size = 0;
