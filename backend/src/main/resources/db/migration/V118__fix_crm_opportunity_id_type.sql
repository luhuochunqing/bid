-- 修复 crm_opportunity_id 列类型：数据库为 bigint，但实体定义为 String（存商机编号 code 如 CC20260610180）。
-- ⚠️ CO-277 实测：CRM 推送的 crmOpportunityId 实为商机主键 id（纯数字如 20916），非 code。
-- CrmTenderLinkService.applyCrmLinkAndAssignment 会自动识别纯数字 id 并反查 code 后落库，
-- 因此本列最终存储的仍是 code（CC... 格式），保证 bidInfoSync 回传时 CRM 能按编号匹配。
-- 涉及 PR 的 CRM 商机关联功能
ALTER TABLE tenders MODIFY COLUMN crm_opportunity_id VARCHAR(64) DEFAULT NULL;
