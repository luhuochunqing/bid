-- V1211: add_sub_topic_to_knowledge_case
-- 说明: 为 knowledge_case 表扩充 sub_topic 业务子主题列与 quality_score 案例文本质量分列，
-- 支持 AI 智能案例推荐中评分项业务子主题对齐与内容充实度加权。

ALTER TABLE knowledge_case
    ADD COLUMN sub_topic VARCHAR(50) NULL COMMENT '业务子主题（如 SLA_SERVICE, SYS_ARCH, LOGISTICS_WAREHOUSE）',
    ADD COLUMN quality_score INT NOT NULL DEFAULT 60 COMMENT '案例文本质量与充实度评分（0-100）',
    ADD INDEX idx_knowledge_case_sub_topic (sub_topic);
