-- V1047: 补齐 knowledge_case.product_line 列
-- KnowledgeCase 实体已包含 productLine 字段，但 V1043 建表时遗漏此列
ALTER TABLE knowledge_case
  ADD COLUMN product_line VARCHAR(100) NULL COMMENT '产品线'
  AFTER customer_type;
