-- 修复 crm_opportunity_id 列类型：数据库为 bigint，但实体定义为 String（存商机编号如 CC20260610180）
-- 涉及 PR 的 CRM 商机关联功能
ALTER TABLE tenders MODIFY COLUMN crm_opportunity_id VARCHAR(64) DEFAULT NULL;
