-- V1210: fix_v1209_archive_backfill_updated_at
-- 说明: 修复 V1209 失败迁移（FlywayMigrateException: Schema contains a failed migration to version 1209）。
-- 根因: V1209 step1 向 project_archive 插入时漏写 updated_at 字段；而
--       project_archive.updated_at datetime(6) NOT NULL 无默认值，MySQL 8.0 STRICT_TRANS_TABLES
--       模式下直接报 ERROR 1364 "Field does not have a default value"，Flyway 单迁移事务整体回滚，
--       flyway_schema_history 记 success=0，导致后续每次启动都被失败迁移阻塞。
-- 注意: 经验证 archive_file 表无 updated_at 列，故 step2 维持与 V1209 一致（不插 updated_at）；
--       仅 project_archive 需补 updated_at=NOW()。
-- 处置: 不回改 V1209（Flyway 不重跑已失败迁移，且保留失败记录供审计），改用新版本号 V1210
--       复刻 V1209 数据回填逻辑并补全 project_archive.updated_at。
-- 幂等: 复用"LEFT JOIN + <id> IS NULL"去重模式，重复执行不产生重复行。
-- 防御: 对潜在超长/空值字段做 LEFT 截断 + COALESCE 兜底，避免 STRICT_TRANS_TABLES 再次中断。

-- 步骤 1：补全所有拥有 project_documents 的项目的 project_archive 归档头
--         （修复 V1209 漏写的 updated_at：project_archive.updated_at NOT NULL 无默认值；
--           LEFT(p.name,255) 防 Data too long）
INSERT INTO project_archive (
    project_id,
    project_name,
    archive_status,
    created_at,
    updated_at
)
SELECT DISTINCT
    p.id AS project_id,
    LEFT(p.name, 255) AS project_name,
    'ACTIVE' AS archive_status,
    NOW() AS created_at,
    NOW() AS updated_at
FROM projects p
INNER JOIN project_documents pd ON pd.project_id = p.id
LEFT JOIN project_archive pa ON pa.project_id = p.id
WHERE pa.id IS NULL;

-- 步骤 2：将 project_documents 中尚未归档的文件回填至 archive_file 表
--         （与 V1209 一致：archive_file 无 updated_at 列；
--           LEFT(COALESCE(file_url),255) 防空值/Data too long）
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
    LEFT(COALESCE(pd.file_url, ''), 255) AS file_path,
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
