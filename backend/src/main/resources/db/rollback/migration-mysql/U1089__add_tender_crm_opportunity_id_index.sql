-- Input: V1089__add_tender_crm_opportunity_id_index.sql
-- CO-297: 回滚 V1089 — 删除 crm_opportunity_id 索引。
-- 应用层反查会随之退化为全表扫描，但 CO-297 修复语义由 Guard 在应用层保证，
-- 回滚仅恢复 schema 变更。
DROP INDEX idx_tender_crm_opportunity_id ON tenders;
