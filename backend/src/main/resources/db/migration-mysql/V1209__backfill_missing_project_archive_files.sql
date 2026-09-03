-- V1209: backfill_missing_project_archive_files
-- 说明: 补全历史项目归档头（project_archive）及未同步的附件（archive_file）。
-- 根因：部分历史项目（如通过特定导入或早期流程创建）未生成 project_archive 记录，
--       或 project_documents 中的投标文件未同步至 archive_file，导致案例库下载标书原文预检报错
--       “标书原文未归档：该项目暂无档案附件”。
-- 幂等：1. project_archive 使用 LEFT JOIN pa.id IS NULL 确保仅对缺失归档头的项目插入
--       2. archive_file 使用 LEFT JOIN af.id IS NULL 结合 file_path 与 name+category 去重，避免重复插入

-- 步骤 1：确保所有拥有 project_documents 的项目都存在 project_archive 归档头
INSERT INTO project_archive (
    project_id,
    project_name,
    archive_status,
    created_at
)
SELECT DISTINCT
    p.id AS project_id,
    p.name AS project_name,
    'ACTIVE' AS archive_status,
    NOW() AS created_at
FROM projects p
INNER JOIN project_documents pd ON pd.project_id = p.id
LEFT JOIN project_archive pa ON pa.project_id = p.id
WHERE pa.id IS NULL;

-- 步骤 2：将 project_documents 中尚未归档的文件回填至 archive_file 表
INSERT INTO archive_file (
    archive_id,
    file_name,
    document_category,
    file_path,
    file_size,
    upload_user_id,
    upload_user_name,
    created_at
)
SELECT
    pa.id AS archive_id,
    pd.name AS file_name,
    CASE pd.document_category
        WHEN 'TENDER_DOCUMENT' THEN 'TENDER'
        WHEN 'BID_DOCUMENT' THEN 'BID'
        WHEN 'EVALUATION_EVIDENCE' THEN 'OPEN_LIST'
        WHEN 'RESULT_EVIDENCE' THEN 'WIN_NOTICE'
        WHEN 'CLOSURE_EVIDENCE' THEN 'DEPOSIT_RECEIPT'
        WHEN 'RETROSPECTIVE_REPORT' THEN 'OTHER'
        ELSE COALESCE(pd.document_category, 'OTHER')
    END AS document_category,
    COALESCE(pd.file_url, '') AS file_path,
    0 AS file_size,
    COALESCE(pd.uploader_id, 0) AS upload_user_id,
    COALESCE(pd.uploader_name, '系统') AS upload_user_name,
    NOW() AS created_at
FROM project_documents pd
INNER JOIN project_archive pa ON pa.project_id = pd.project_id
LEFT JOIN archive_file af ON af.archive_id = pa.id
    AND (
        (pd.file_url IS NOT NULL AND pd.file_url != '' AND af.file_path = pd.file_url)
        OR (af.file_name = pd.name AND af.document_category = CASE pd.document_category
            WHEN 'TENDER_DOCUMENT' THEN 'TENDER'
            WHEN 'BID_DOCUMENT' THEN 'BID'
            WHEN 'EVALUATION_EVIDENCE' THEN 'OPEN_LIST'
            WHEN 'RESULT_EVIDENCE' THEN 'WIN_NOTICE'
            WHEN 'CLOSURE_EVIDENCE' THEN 'DEPOSIT_RECEIPT'
            WHEN 'RETROSPECTIVE_REPORT' THEN 'OTHER'
            ELSE COALESCE(pd.document_category, 'OTHER')
        END)
    )
WHERE af.id IS NULL;
