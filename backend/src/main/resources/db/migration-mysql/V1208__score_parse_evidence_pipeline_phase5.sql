-- V1208: 评分解析证据化 Phase 5 数据库迁移（spec 059 Phase 5）
--
-- 背景：为支撑评分事实（ScoreFact）、原文证据（ScoreEvidence）与复核审计（ScoreParseReview）的
--   不可变持久化存储，并在 score_item 上追加证据与血缘列以供下游双写兼容：
--   1. score_parse_task 追加 artifact_sha256 与 review_reason
--   2. score_item 追加 source_cell_ids, weight_evidence, extraction_method, confidence, validation_status, parent_score_item_id（全部允许 NULL）
--   3. 新增 score_parse_fact、score_parse_evidence、score_parse_review 三张表
-- Source: specs/059-score-parse-evidence-pipeline/data-model.md；回滚见 rollback/migration-mysql/U1208

ALTER TABLE score_parse_task
    ADD COLUMN artifact_sha256 VARCHAR(64) NULL COMMENT '文档 artifact sha256 摘要' AFTER fallback_reason,
    ADD COLUMN review_reason   VARCHAR(256) NULL COMMENT '人工复核理由或降级原因' AFTER artifact_sha256;

ALTER TABLE score_item
    ADD COLUMN source_cell_ids       JSON NULL COMMENT '来源单元格编号列表' AFTER location,
    ADD COLUMN weight_evidence       JSON NULL COMMENT '权重确定性证据' AFTER source_cell_ids,
    ADD COLUMN extraction_method     VARCHAR(64) NULL COMMENT '提取方法' AFTER weight_evidence,
    ADD COLUMN confidence            DECIMAL(5, 4) NULL COMMENT '置信度' AFTER extraction_method,
    ADD COLUMN validation_status     VARCHAR(32) NULL COMMENT '校验状态' AFTER confidence,
    ADD COLUMN parent_score_item_id  BIGINT NULL COMMENT '父级评分项 ID' AFTER validation_status;

CREATE TABLE score_parse_fact (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id        BIGINT NOT NULL COMMENT '项目 ID',
    parse_task_id     BIGINT NOT NULL COMMENT '解析任务 ID',
    fact_id           VARCHAR(64) NOT NULL COMMENT '事实唯一标识',
    source_table_id   VARCHAR(64) NULL COMMENT '来源表格 ID',
    source_cell_ids   JSON NULL COMMENT '来源单元格编号列表',
    source_text       TEXT NOT NULL COMMENT '事实原文',
    code              VARCHAR(50) NULL COMMENT '评分编号',
    dimension         VARCHAR(200) NOT NULL COMMENT '评分维度',
    weight            DECIMAL(6, 2) NOT NULL COMMENT '权重分值',
    weight_evidence   JSON NULL COMMENT '权重证据',
    parent_fact_id    VARCHAR(64) NULL COMMENT '父级事实 ID',
    extraction_method VARCHAR(64) NOT NULL COMMENT '提取方式',
    confidence        DECIMAL(5, 4) NOT NULL DEFAULT 1.0000 COMMENT '置信度',
    validation_status VARCHAR(32) NOT NULL DEFAULT 'AUTO_APPROVED' COMMENT '校验状态',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_fact_task (parse_task_id),
    INDEX idx_fact_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可变评分事实快照表';

CREATE TABLE score_parse_evidence (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    fact_id       VARCHAR(64) NOT NULL COMMENT '所属事实 ID',
    parse_task_id BIGINT NOT NULL COMMENT '解析任务 ID',
    cell_id       VARCHAR(64) NULL COMMENT '单元格 ID',
    kind          VARCHAR(64) NOT NULL COMMENT '证据类型',
    quoted_text   TEXT NULL COMMENT '引用原文片段',
    start_offset  INT NULL COMMENT '起始偏移量',
    end_offset    INT NULL COMMENT '结束偏移量',
    explanation   VARCHAR(255) NULL COMMENT '解释说明',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_evidence_fact (fact_id),
    INDEX idx_evidence_task (parse_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评分原文单元格证据表';

CREATE TABLE score_parse_review (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT NOT NULL COMMENT '项目 ID',
    parse_task_id   BIGINT NOT NULL COMMENT '解析任务 ID',
    fact_id         VARCHAR(64) NULL COMMENT '关联事实 ID',
    action          VARCHAR(32) NOT NULL COMMENT '动作：RETAIN_ORIGINAL / MANUAL_ADJUST / EXCLUDE',
    original_weight DECIMAL(6, 2) NULL COMMENT '调整前原始权重',
    adjusted_weight DECIMAL(6, 2) NULL COMMENT '调整后权重',
    reason          TEXT NOT NULL COMMENT '人工调整或复核理由',
    operator_id     BIGINT NULL COMMENT '操作人用户 ID',
    operator_name   VARCHAR(64) NULL COMMENT '操作人姓名',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_review_task (parse_task_id),
    INDEX idx_review_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评分解析人工复核审计表';
