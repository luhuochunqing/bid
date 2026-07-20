-- spec 039: V1171 回填 OBS 直传历史文档到 archive_file 表
-- 蓝图 §4.1.1.1：项目文档上传时即时按分类归档到项目档案（本迁移修复历史缺失数据）。
-- 根因：旧版 ProjectDocumentWorkflowService.createProjectDocument（JSON API 路径）未调用 attachFileToArchive，
--       导致通过 OBS 直传上传的投标文件等文档只写入 project_documents 表，未写入 archive_file 表，
--       前端项目档案视图缺失这些文档。
-- 修复：spec 039 已将归档逻辑上提到 createProjectDocument 末尾统一触发，新上传的文档会正常归档。
--       本迁移回填历史已通过 OBS 直传上传但未归档的记录。
-- 幂等：LEFT JOIN archive_file af ON af.archive_id = pa.id AND af.file_path = pd.file_url + WHERE af.id IS NULL
--       重复执行不会产生重复记录
-- 维护声明: source migration changes must update this rollback script in the same branch.

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
    -- SQL 层归一化分类（与 DocumentCategoryNormalizer ALIAS 映射一致）
    CASE pd.document_category
        WHEN 'TENDER_DOCUMENT' THEN 'TENDER'
        WHEN 'BID_DOCUMENT' THEN 'BID'
        WHEN 'EVALUATION_EVIDENCE' THEN 'OPEN_LIST'
        WHEN 'RESULT_EVIDENCE' THEN 'WIN_NOTICE'
        WHEN 'CLOSURE_EVIDENCE' THEN 'DEPOSIT_RECEIPT'
        WHEN 'RETROSPECTIVE_REPORT' THEN 'OTHER'
        ELSE COALESCE(pd.document_category, 'OTHER')
    END AS document_category,
    pd.file_url AS file_path,
    0 AS file_size,  -- project_documents.size 是 VARCHAR，无法可靠解析为字节
    COALESCE(pd.uploader_id, 0) AS upload_user_id,
    COALESCE(pd.uploader_name, '系统') AS upload_user_name,
    NOW() AS created_at
FROM project_documents pd
INNER JOIN project_archive pa ON pa.project_id = pd.project_id
LEFT JOIN archive_file af ON af.archive_id = pa.id AND af.file_path = pd.file_url
WHERE pd.file_url LIKE 'obs-direct:%'
  AND af.id IS NULL;
