-- V1055: Add soft delete columns to performance_attachment table
-- §4.5.1 #3/5 业绩管理 — soft delete support for attachments with audit trail

ALTER TABLE performance_attachment
    ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已删除: 0=否, 1=是',
    ADD COLUMN deleted_at DATETIME DEFAULT NULL COMMENT '删除时间',
    ADD COLUMN deleted_by VARCHAR(100) DEFAULT NULL COMMENT '删除人（用户名）';

-- 为软删除查询添加索引
CREATE INDEX idx_attachment_deleted ON performance_attachment(is_deleted);

