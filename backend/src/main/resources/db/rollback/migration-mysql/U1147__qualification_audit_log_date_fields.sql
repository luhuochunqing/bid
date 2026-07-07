-- Input: migration-mysql/V1147__qualification_audit_log_date_fields.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1147: 回滚 CO-530 字段变更
-- 1. 删除 audit_log_file_url 列
-- 2. cert_review_note: DATE → VARCHAR(200)
-- 数据丢失说明：audit_log_file_url 列中已写入的审核日志附件 URL 会丢失；
--   cert_review_note 从 DATE 转回 VARCHAR(200) 安全（MySQL 自动格式化为 'YYYY-MM-DD'）。
--   回滚前请确认是否可接受。

-- 步骤 1：删除审核日志附件列
ALTER TABLE business_qualifications DROP COLUMN audit_log_file_url;

-- 步骤 2：cert_review_note 回退为 VARCHAR(200)
-- DATE → VARCHAR 转换安全：MySQL 会自动将 DATE 转为 'YYYY-MM-DD' 字符串
ALTER TABLE business_qualifications MODIFY COLUMN cert_review_note VARCHAR(200) NULL;
