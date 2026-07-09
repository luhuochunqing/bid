-- Input: V1161__ca_related_platforms_text.sql
-- U1161: 回滚 V1161（CO-566 关联平台改文本）
-- 反向操作：
--   1. 恢复 ca_certificates.platform_ids 列（VARCHAR(512)）
--   2. 从 ca_certificate_platforms 关联表回填 platform_ids（逗号分隔 ID 串）
--   3. 删除 related_platforms 文本列
-- 注意：CO-566 后通过文本输入的关联平台（不在关联表里的）无法回填到 platform_ids，
--       回滚后这部分纯文本关联信息会丢失（可接受——回滚是紧急降级，非日常操作）。

-- 1. 恢复 platform_ids 列
ALTER TABLE ca_certificates
    ADD COLUMN platform_ids VARCHAR(512) DEFAULT NULL COMMENT '关联投标平台ID列表，逗号分隔' AFTER ca_password;

-- 2. 从关联表回填 platform_ids（逗号分隔 ID）
UPDATE ca_certificates cc
JOIN (
    SELECT
        ccp.ca_certificate_id,
        GROUP_CONCAT(ccp.platform_account_id ORDER BY ccp.platform_account_id SEPARATOR ',') AS platform_id_list
    FROM ca_certificate_platforms ccp
    GROUP BY ccp.ca_certificate_id
) backfill ON backfill.ca_certificate_id = cc.id
SET cc.platform_ids = backfill.platform_id_list
WHERE cc.platform_ids IS NULL;

-- 3. 删除 related_platforms 文本列
ALTER TABLE ca_certificates DROP COLUMN related_platforms;
