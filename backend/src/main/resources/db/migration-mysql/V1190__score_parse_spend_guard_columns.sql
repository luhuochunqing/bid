-- V1189: score_parse 花费守卫列（触发来源、投标指纹、结果沿用）
ALTER TABLE score_parse_task
    ADD COLUMN trigger_source VARCHAR(16) NULL COMMENT 'AUTO/MANUAL' AFTER error_message,
    ADD COLUMN bid_content_hash VARCHAR(64) NULL COMMENT '投标文件字节SHA-256' AFTER trigger_source,
    ADD COLUMN item_set_hash VARCHAR(64) NULL COMMENT '评分项清单指纹' AFTER bid_content_hash,
    ADD COLUMN chapter_hashes TEXT NULL COMMENT '章节标题到哈希的JSON' AFTER item_set_hash;

ALTER TABLE score_result
    ADD COLUMN reuse_kind VARCHAR(16) NULL COMMENT 'FRESH/REUSED' AFTER match_ratio;
