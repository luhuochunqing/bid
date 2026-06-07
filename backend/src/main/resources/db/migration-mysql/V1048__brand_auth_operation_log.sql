-- V1048: Create brand authorization operation log table

CREATE TABLE IF NOT EXISTS brand_auth_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    authorization_id BIGINT NOT NULL COMMENT '关联授权ID',
    operator_id BIGINT COMMENT '操作人用户ID',
    operator_username VARCHAR(100) NOT NULL COMMENT '操作人姓名/角色/工号',
    action_type VARCHAR(50) NOT NULL COMMENT '操作类型',
    summary VARCHAR(255) COMMENT '变更摘要',
    details TEXT COMMENT '变更详情 (前后值对照 JSON)',
    remarks TEXT COMMENT '备注（例如作废原因）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_baol_auth_id (authorization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌授权操作日志';
