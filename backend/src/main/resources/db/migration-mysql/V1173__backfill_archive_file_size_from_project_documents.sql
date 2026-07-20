-- 档案修复：回填 archive_file.file_size = 0 的历史记录
-- 根因：spec 039 OBS 直传归档时 file_size 传 0L（project_documents.size 是 VARCHAR 无法可靠解析回字节），
--       导致档案详情抽屉"大小"列显示 0B。
-- 修复：
--   1. 新上传的 OBS 直传文档由 ProjectDocumentWorkflowService.createProjectDocument 从 request.fileSizeBytes 获取真实字节数。
--   2. 本迁移回填历史 archive_file.file_size = 0 的记录，通过 file_path 关联 project_documents.file_url，
--      从 project_documents.size（VARCHAR 如 "1.5MB"）解析为字节。
-- 幂等：WHERE af.file_size = 0 限定，重复执行不会再次更新已修复的记录。
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- project_documents.size 字段格式可能为：
--   "1024"        → 纯数字（视为字节）
--   "1.5MB"       → MB 单位
--   "1024KB"      → KB 单位
--   "1024B"       → B 单位
--   其他无法解析的格式 → 保持 0（避免误改）
-- 解析使用多层 CASE WHEN + CAST + ROUND，覆盖常见格式。
-- MySQL 8.0 REGEXP_REPLACE 用于去除单位后缀，CAST DECIMAL(20,2) 保证精度。

UPDATE archive_file af
INNER JOIN project_documents pd ON pd.file_url = af.file_path
SET af.file_size = GREATEST(0, CASE
    -- 纯数字（无单位后缀，视为字节）
    WHEN pd.size REGEXP '^[0-9]+$' THEN CAST(pd.size AS UNSIGNED)
    -- 带 B 后缀（如 "1024B"）
    WHEN UPPER(pd.size) REGEXP '^[0-9]+B$' THEN CAST(REGEXP_REPLACE(pd.size, '[Bb]', '') AS UNSIGNED)
    -- 带 KB 后缀（如 "1.5KB" 或 "1024KB"）
    WHEN UPPER(pd.size) REGEXP '^[0-9.]+KB$'
        THEN ROUND(CAST(REGEXP_REPLACE(UPPER(pd.size), 'KB', '') AS DECIMAL(20,4)) * 1024)
    -- 带 MB 后缀（如 "1.5MB"）
    WHEN UPPER(pd.size) REGEXP '^[0-9.]+MB$'
        THEN ROUND(CAST(REGEXP_REPLACE(UPPER(pd.size), 'MB', '') AS DECIMAL(20,4)) * 1024 * 1024)
    -- 带 GB 后缀（如 "1.5GB"）
    WHEN UPPER(pd.size) REGEXP '^[0-9.]+GB$'
        THEN ROUND(CAST(REGEXP_REPLACE(UPPER(pd.size), 'GB', '') AS DECIMAL(20,4)) * 1024 * 1024 * 1024)
    -- 其他无法解析的格式保持 0
    ELSE 0
END)
WHERE af.file_size = 0
  AND pd.size IS NOT NULL
  AND pd.size != '';
