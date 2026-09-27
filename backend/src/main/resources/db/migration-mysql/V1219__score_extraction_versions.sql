-- V1219: Frozen extraction candidates and CAS activation history (spec 073).
-- Incremental only: historical payloads remain NULL; active items remain computable-only.
ALTER TABLE score_parse_fact
    MODIFY COLUMN weight DECIMAL(6,2) NULL COMMENT '原始分值，未知为NULL',
    ADD COLUMN item_payload JSON NULL COMMENT '完整候选快照',
    ADD COLUMN payload_version INT NULL COMMENT '候选结构版本，历史为空';
ALTER TABLE score_item
    ADD COLUMN source_fact_id VARCHAR(64) NULL COMMENT '原始事实身份',
    ADD COLUMN active_version_id BIGINT NULL COMMENT '生效版本身份',
    ADD COLUMN rule_fingerprint CHAR(64) NULL COMMENT '完整规则摘要';
CREATE TABLE score_extraction_snapshot (
    parse_task_id BIGINT PRIMARY KEY COMMENT '解析任务及模式标记',
    project_id BIGINT NOT NULL COMMENT '项目身份',
    base_fingerprint VARCHAR(128) NOT NULL COMMENT '启动时活动指纹',
    payload JSON NULL COMMENT '全量候选及方案和运行诊断',
    frozen_at DATETIME(6) NULL COMMENT '冻结时间，冻结后不可覆盖',
    INDEX idx_extraction_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='解析快照及任务模式';
CREATE TABLE score_active_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '版本单调身份',
    project_id BIGINT NOT NULL COMMENT '项目身份',
    parse_task_id BIGINT NOT NULL COMMENT '解析来源任务',
    scheme_id VARCHAR(255) NOT NULL COMMENT '选择方案',
    previous_version_id BIGINT NULL COMMENT '前版本身份',
    projection_fingerprint CHAR(64) NOT NULL COMMENT '生效时完整投影摘要',
    selection_payload JSON NOT NULL COMMENT '完整选择项身份',
    activation_source VARCHAR(16) NOT NULL COMMENT 'AUTO或MANUAL',
    has_manual_edits BOOLEAN NOT NULL DEFAULT FALSE COMMENT '人工选择保护',
    reason TEXT NOT NULL COMMENT '激活原因',
    operator_name VARCHAR(200) NULL COMMENT '操作人',
    created_at DATETIME(6) NOT NULL COMMENT '激活时间',
    INDEX idx_active_project (project_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变评分版本及激活审计';
