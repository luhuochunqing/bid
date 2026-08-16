-- V1187: AI 评分标准解析三表（spec 041）
-- 需求：AI 评分标准解析 — 后端服务（解析/打分/知识库匹配）
-- score_parse_task 任务表 / score_item 评分项（阶段 1）/ score_result 打分结果（阶段 2）
-- 设计依据：specs/041-ai-score-parse-backend/data-model.md
CREATE TABLE IF NOT EXISTS score_parse_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL COMMENT 'UUID，对外标识',
    project_id BIGINT NOT NULL COMMENT '关联项目 ID',
    task_type VARCHAR(20) NOT NULL COMMENT 'PARSE / SCORING',
    status VARCHAR(20) NOT NULL COMMENT 'PENDING / PROCESSING / COMPLETED / FAILED',
    progress INT NOT NULL DEFAULT 0 COMMENT '进度 0-100',
    stage VARCHAR(50) NULL COMMENT '进度阶段（召回/提取/校验/匹配/打分）',
    file_name VARCHAR(255) NULL COMMENT '触发文件名',
    file_url VARCHAR(500) NULL COMMENT 'doc-insight:// URL',
    error_message TEXT NULL COMMENT '失败原因',
    timeout_marked TINYINT(1) NOT NULL DEFAULT 0 COMMENT '超时扫描 job 标记',
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE INDEX uk_task_id (task_id),
    INDEX idx_spt_project_type_status (project_id, task_type, status),
    -- 超时扫描：status=PROCESSING AND updated_at < now-30min
    INDEX idx_spt_status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 评分解析/打分任务表';

CREATE TABLE IF NOT EXISTS score_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL COMMENT '冗余项目 ID 便于直查',
    parse_task_id BIGINT NOT NULL COMMENT '产生本批的解析任务 ID',
    item_index INT NOT NULL COMMENT '表内序号（编号重复时去重保留首次出现）',
    code VARCHAR(50) NOT NULL COMMENT '评分项编号（原文提取，如 A1/B2）',
    dim VARCHAR(200) NOT NULL COMMENT '评分项名称',
    detail TEXT NOT NULL COMMENT '详细要素（完整保留原文）',
    weight DECIMAL(6,2) NOT NULL COMMENT '权重绝对分值',
    score_type VARCHAR(20) NOT NULL COMMENT 'OBJECTIVE / SUBJECTIVE',
    status_stage1 VARCHAR(20) NOT NULL COMMENT 'OK / DANGER / PENDING',
    est_score DECIMAL(6,2) NULL COMMENT '预计得分；主观项 NULL',
    est_basis TEXT NULL COMMENT '阶段 1 评分依据',
    kb_hit TINYINT(1) NULL COMMENT '知识库命中标记（仅客观项可 true）',
    context_note TEXT NULL COMMENT '评分规则上下文（注/说明/备注）',
    source_text TEXT NULL COMMENT '原文依据',
    location VARCHAR(200) NULL COMMENT '页码/位置',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_si_project (project_id),
    INDEX idx_si_parse_task (parse_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评分项（阶段 1 解析产物）';

CREATE TABLE IF NOT EXISTS score_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    score_item_id BIGINT NOT NULL COMMENT '1:1 关联评分项',
    scoring_task_id BIGINT NOT NULL COMMENT '产生本结果的打分任务 ID',
    actual_score DECIMAL(6,2) NULL COMMENT '实际得分；主观项/异常项 NULL',
    status_stage2 VARCHAR(20) NOT NULL COMMENT 'OK / DANGER / PENDING',
    evidence TEXT NULL COMMENT '评分依据',
    quote TEXT NULL COMMENT '标书引用原文（含章节页码）；无则 NULL',
    missed_reason TEXT NULL COMMENT '缺失说明',
    suggestion TEXT NULL COMMENT '修改建议（主观项/待确认/不满足项）',
    match_ratio INT NULL COMMENT '匹配比例 0-100',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE INDEX uk_sr_score_item (score_item_id),
    INDEX idx_sr_scoring_task (scoring_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打分结果（阶段 2 产物）';
