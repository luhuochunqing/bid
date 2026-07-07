-- CO-464: Tender 新增 purchaser_id 字段（招标主体ID，与 CRM 招标主体主键对应）
-- 来源：
--   1. 创建/修改接口前端传入（CO-500）
--   2. 关联商机时由 check-tender-subject 接口的 data 字段返回（CO-501）
ALTER TABLE tenders ADD COLUMN purchaser_id BIGINT NULL COMMENT '招标主体ID（CRM招标主体主键）';
