-- ================================================================
-- V141: Cross-Field Validation Rules
-- 功能：新增 cross_field_validation_rule 表，支持跨字段验证。
-- 支持的操作符：less_than / greater_than / equals / not_equals /
--              sum_equals / one_filled / both_filled / not_after
-- ================================================================

CREATE TABLE IF NOT EXISTS cross_field_validation_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    definition_id   BIGINT NOT NULL COMMENT '关联 form_definition_registry.id',
    scope           VARCHAR(100) NOT NULL COMMENT '所属 scope，关联 form_definition_registry.scope',
    source_field    VARCHAR(100) NOT NULL COMMENT '源字段 key',
    operator        VARCHAR(50) NOT NULL COMMENT '操作符：less_than / greater_than / equals / not_equals / sum_equals / one_filled / both_filled / not_after',
    target_field    VARCHAR(100) DEFAULT NULL COMMENT '目标字段 key（one_filled/both_filled 可为空）',
    target_value    VARCHAR(500) DEFAULT NULL COMMENT '目标常量值（当 target_field 为空时使用）',
    error_message  VARCHAR(500) NOT NULL COMMENT '验证失败时的错误提示',
    priority        INT NOT NULL DEFAULT 0 COMMENT '优先级，数值越小越先执行',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    CONSTRAINT fk_cfvr_def FOREIGN KEY (definition_id) REFERENCES form_definition_registry(id) ON DELETE CASCADE,
    INDEX idx_cfvr_definition_id (definition_id),
    INDEX idx_cfvr_scope (scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨字段验证规则表';
