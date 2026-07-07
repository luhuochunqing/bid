-- U1147: 回滚 CO-530 字段变更
-- 1. 删除 audit_log_file_url 列
-- 2. cert_review_note: DATE → VARCHAR(200)

-- 步骤 1：删除审核日志附件列
ALTER TABLE business_qualifications DROP COLUMN audit_log_file_url;

-- 步骤 2：cert_review_note 回退为 VARCHAR(200)
-- DATE → VARCHAR 转换安全：MySQL 会自动将 DATE 转为 'YYYY-MM-DD' 字符串
ALTER TABLE business_qualifications MODIFY COLUMN cert_review_note VARCHAR(200) NULL;
