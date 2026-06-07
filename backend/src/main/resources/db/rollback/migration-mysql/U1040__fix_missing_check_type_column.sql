-- Input: V1040__fix_missing_check_type_column.sql
-- Rollback for V1040__fix_missing_check_type_column.sql

-- 删除新增的合规规则（25项标书文档质量核查规则）
DELETE FROM compliance_rules WHERE rule_definition LIKE '%"checkItem":%';

-- 删除索引
DROP INDEX idx_result_check_type ON compliance_check_results;

-- 删除列
ALTER TABLE compliance_check_results DROP COLUMN check_type;
