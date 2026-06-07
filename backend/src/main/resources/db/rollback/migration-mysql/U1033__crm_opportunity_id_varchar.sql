-- Input: V1033__crm_opportunity_id_varchar.sql
-- Rollback for V1033__crm_opportunity_id_varchar.sql
ALTER TABLE tenders MODIFY COLUMN crm_opportunity_id BIGINT NULL COMMENT '关联CRM商机ID';
