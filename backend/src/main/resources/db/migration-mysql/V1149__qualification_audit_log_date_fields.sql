-- V1149: CO-530 资质证书审核提醒字段类型变更 + 新增审核日志附件字段
-- （原 V1147 → V1148 → V1149：V1147 与 CO-533 撞号，V1148 与 CO-537 撞号，递增至 V1149）
-- 1. cert_review_note: VARCHAR(200) → DATE（证书审核提醒改为日期选择）
-- 2. 新增 audit_log_file_url: VARCHAR(500)（审核日志附件 URL，非必填）

-- 步骤 1：清理非日期格式数据（仅保留 YYYY-MM-DD 格式，其他置 NULL）
UPDATE business_qualifications
SET cert_review_note = NULL
WHERE cert_review_note IS NOT NULL
  AND cert_review_note != ''
  AND cert_review_note NOT REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}$';

-- 步骤 2：清理空字符串为 NULL（避免 MODIFY 时报错）
UPDATE business_qualifications SET cert_review_note = NULL WHERE cert_review_note = '';

-- 步骤 3：修改列类型 VARCHAR(200) → DATE
ALTER TABLE business_qualifications MODIFY COLUMN cert_review_note DATE NULL;

-- 步骤 4：新增审核日志附件字段
ALTER TABLE business_qualifications ADD COLUMN audit_log_file_url VARCHAR(500) NULL;
