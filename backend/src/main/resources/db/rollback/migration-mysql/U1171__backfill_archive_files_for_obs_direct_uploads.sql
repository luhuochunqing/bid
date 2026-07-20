-- Input: migration-mysql/V1171__backfill_archive_files_for_obs_direct_uploads.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway rollback coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- 回滚 V1171：删除由 V1171 回填插入的 archive_file 记录
-- 三重限定收窄删除面：
--   1) file_path LIKE 'obs-direct:%' —— V1171 只回填 obs-direct: 前缀的文档；
--      multipart 归档的 file_path 是本地物理路径（spec 039 修复后恢复物理路径语义），无误删风险
--   2) file_size = 0 —— V1171 回填行 file_size 恒为 0（project_documents.size 是 VARCHAR 无法解析）；
--      multipart 新归档写入真实字节数，不受本条件命中
--   3) created_at 时间窗 —— 圈定 V1171 执行时段
-- 注：V1171 之后新上传的 OBS 直传文档（JSON 路径，file_size 同为 0）在时间窗内会被一并删除；
--     这些行由 project_documents 表派生，误删后手动重跑 V1171 的 INSERT SELECT 即可幂等恢复。
-- Manual rollback required: created_at 时间范围是启发式，仅覆盖部署后 1 小时内的回滚；
--     超时回滚需先查 flyway_schema_history 中 version='1171' 的 installed_on，
--     把 INTERVAL 调整为覆盖该时点的窗口后再执行。
DELETE FROM archive_file
WHERE file_path LIKE 'obs-direct:%'
  AND file_size = 0
  AND created_at >= NOW() - INTERVAL 1 HOUR;
