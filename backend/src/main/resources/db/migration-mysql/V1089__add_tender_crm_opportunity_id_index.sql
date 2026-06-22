-- CO-297: 标讯关联 CRM 商机时做去重检查。
-- 为 crm_opportunity_id 建非唯一索引（标讯创建时可为空，关联后写入；同一商机仅允许一个标讯占用，
-- 唯一性由应用层在写入前通过 findFirstByCrmOpportunityId 校验，而非数据库唯一约束，
-- 因为 ABANDONED/已归档标的历史关联记录会继续占用该商机 ID，业务层策略更可控）。
CREATE INDEX idx_tender_crm_opportunity_id ON tenders (crm_opportunity_id);
