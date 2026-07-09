-- V1161: CO-566 关联平台字段改为文本
-- 背景：关联平台原本通过 ca_certificate_platforms 多对多关联表存平台账号ID，
--       再 ID→名称反查展示。产品需求（CO-566）要求改为单行文本，直接存名称。
-- 本次变更：
--   1. ca_certificates 新增 related_platforms VARCHAR(500) 文本列（直接存逗号分隔的平台名称）
--   2. 从 ca_certificate_platforms 关联表回填存量数据（JOIN platform_accounts 取名称拼接）
--   3. 删除遗留的 platform_ids 列（自 V1073 起已废弃，实际数据在关联表里）
--   4. 关联表 ca_certificate_platforms 在本迁移后不再被代码读写，保留物理表（后续任务可清理）

-- 1. 新增文本列
ALTER TABLE ca_certificates
    ADD COLUMN related_platforms VARCHAR(500) DEFAULT NULL COMMENT '关联平台名称（文本，多个用逗号分隔）' AFTER ca_platform_url;

-- 2. 存量数据回填：关联表 + platform_accounts JOIN → 逗号分隔名称串
UPDATE ca_certificates cc
JOIN (
    SELECT
        ccp.ca_certificate_id,
        GROUP_CONCAT(DISTINCT pa.account_name ORDER BY pa.account_name SEPARATOR ',') AS platform_names
    FROM ca_certificate_platforms ccp
    JOIN platform_accounts pa ON pa.id = ccp.platform_account_id
    GROUP BY ccp.ca_certificate_id
) backfill ON backfill.ca_certificate_id = cc.id
SET cc.related_platforms = backfill.platform_names
WHERE cc.related_platforms IS NULL;

-- 3. 删除遗留的 platform_ids 列（V1073 起已废弃，不再被代码读写）
ALTER TABLE ca_certificates DROP COLUMN platform_ids;
