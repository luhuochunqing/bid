-- V1195: add_original_total_weight
-- 说明: score_parse_task 新增 original_total_weight 列，记录权重归一化前的原始合计（spec 047 US3 FR-005）
ALTER TABLE score_parse_task ADD COLUMN original_total_weight DECIMAL(10,2) DEFAULT NULL COMMENT '权重归一化前原始合计；null 表示未归一化';