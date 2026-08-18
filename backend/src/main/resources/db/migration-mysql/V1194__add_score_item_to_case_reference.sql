-- V1193: 为 case_reference_records 增加 project_id, score_item_id, reuse_mode 字段，支持评分项级别的案例复用追溯
ALTER TABLE case_reference_records
    ADD COLUMN project_id BIGINT NULL COMMENT '当前复用目标项目ID',
    ADD COLUMN score_item_id BIGINT NULL COMMENT '关联的评分项ID',
    ADD COLUMN reuse_mode VARCHAR(20) NULL DEFAULT 'APPEND' COMMENT '复用模式: APPEND/OVERWRITE',
    ADD INDEX idx_case_ref_project (project_id),
    ADD INDEX idx_case_ref_score_item (score_item_id);
