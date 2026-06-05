-- 项目评估表单7个字段
-- V116__project_evaluation_form_fields.sql

ALTER TABLE project_evaluation
    ADD COLUMN background TEXT COMMENT '项目背景',
    ADD COLUMN competitors TEXT COMMENT '竞争对手情况',
    ADD COLUMN contract_period VARCHAR(64) COMMENT '项目合同周期',
    ADD COLUMN shortlisted_bidders INT COMMENT '入围家数',
    ADD COLUMN platform_fee DECIMAL(14,2) COMMENT '平台服务费',
    ADD COLUMN previous_bid TEXT COMMENT '上一轮报价情况',
    ADD COLUMN recommendation TINYINT(1) COMMENT '建议是否投标';
