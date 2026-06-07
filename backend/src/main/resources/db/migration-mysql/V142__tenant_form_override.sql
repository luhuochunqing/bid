-- ================================================================
-- V142: Tenant Form Field Override
-- 功能：新增 tenant_form_field_override 表，支持租户级字段覆盖。
-- 覆盖类型：label / required / default_value / options / hidden / readonly
-- ================================================================

CREATE TABLE IF NOT EXISTS tenant_form_field_override (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    definition_id   BIGINT NOT NULL COMMENT '关联 form_definition_registry.id',
    org_id          BIGINT NOT NULL COMMENT '租户 ID',
    field_key       VARCHAR(100) NOT NULL COMMENT '字段 key',
    override_type   VARCHAR(50) NOT NULL COMMENT '覆盖类型：label / required / default_value / options / hidden / readonly',
    override_value TEXT DEFAULT NULL COMMENT '覆盖值（JSON 序列化后的值）',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    CONSTRAINT fk_tfo_def FOREIGN KEY (definition_id) REFERENCES form_definition_registry(id) ON DELETE CASCADE,
    UNIQUE KEY uk_def_org_field_type (definition_id, org_id, field_key, override_type),
    INDEX idx_tfo_definition_id (definition_id),
    INDEX idx_tfo_org_id (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表单字段覆盖表';
