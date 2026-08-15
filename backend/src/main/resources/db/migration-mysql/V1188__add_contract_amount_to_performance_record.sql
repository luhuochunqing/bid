-- V1188: 业绩记录表新增合同金额列（spec 041）
-- 需求：AI 评分标准解析 — 业绩类评分项匹配需要合同金额门槛比对（research R7）
-- 存量行 NULL 视为"金额未知"，匹配时跳过金额门槛比对（不因 NULL 失配）。
ALTER TABLE performance_record
    ADD COLUMN contract_amount DECIMAL(15,2) NULL COMMENT '合同金额（元）；NULL 视为金额未知' AFTER contract_name;
