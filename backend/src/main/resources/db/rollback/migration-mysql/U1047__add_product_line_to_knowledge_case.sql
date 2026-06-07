-- Input: V1047__add_product_line_to_knowledge_case.sql
-- Rollback for V1047__add_product_line_to_knowledge_case.sql

-- U1047: 回滚 knowledge_case.product_line 列
ALTER TABLE knowledge_case DROP COLUMN IF EXISTS product_line;
