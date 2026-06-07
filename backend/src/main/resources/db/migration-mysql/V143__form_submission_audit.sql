-- ================================================================
-- V143: Form Submission Audit Log
-- 功能：新增 form_submission_audit 表，记录每次表单提交（成功/失败）。
-- ================================================================

CREATE TABLE IF NOT EXISTS form_submission_audit (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    definition_id           BIGINT NOT NULL COMMENT '关联 form_definition_registry.id',
    scope                   VARCHAR(100) NOT NULL COMMENT '表单 scope',
    operator_username       VARCHAR(100) NOT NULL COMMENT '操作人用户名',
    org_id                  BIGINT DEFAULT NULL COMMENT '租户 ID',
    form_data_hash          VARCHAR(64) NOT NULL COMMENT '表单数据 SHA-256 哈希（去重和快速检索）',
    form_data_snapshot      TEXT DEFAULT NULL COMMENT '表单数据 JSON 快照',
    status                  VARCHAR(20) NOT NULL COMMENT '提交状态：SUCCESS / VALIDATION_FAILED / PROCESSING_ERROR',
    error_message           TEXT DEFAULT NULL COMMENT '错误信息（失败时）',
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    CONSTRAINT fk_fsa_def FOREIGN KEY (definition_id) REFERENCES form_definition_registry(id),
    INDEX idx_fsa_definition_id (definition_id),
    INDEX idx_fsa_operator (operator_username),
    INDEX idx_fsa_org_id (org_id),
    INDEX idx_fsa_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单提交审计日志表';
