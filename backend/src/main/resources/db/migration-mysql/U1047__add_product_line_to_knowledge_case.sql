-- Undo V1047: add product_line column to knowledge_case table
-- PR: <填写 URL>
-- Safety check: only run if the column exists
ALTER TABLE knowledge_case DROP COLUMN IF EXISTS product_line;
