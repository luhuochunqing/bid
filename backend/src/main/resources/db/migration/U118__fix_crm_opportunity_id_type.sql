-- U118 回滚：将 crm_opportunity_id 恢复为 bigint
-- 注意：已有字符串值（商机编号）会丢失
ALTER TABLE tenders MODIFY COLUMN crm_opportunity_id BIGINT DEFAULT NULL;
