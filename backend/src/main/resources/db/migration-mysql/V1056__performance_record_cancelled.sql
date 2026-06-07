-- V1056: Add cancelled fields to performance_record for void operation support
-- §4.5.1 #4/5 业绩管理 — cancellation workflow with reason and audit

ALTER TABLE performance_record
    ADD COLUMN cancelled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已作废',
    ADD COLUMN cancelled_at DATETIME DEFAULT NULL COMMENT '作废时间',
    ADD COLUMN cancelled_by VARCHAR(100) DEFAULT NULL COMMENT '作废人',
    ADD COLUMN cancel_reason VARCHAR(500) DEFAULT NULL COMMENT '作废原因';

CREATE INDEX idx_performance_cancelled ON performance_record(cancelled);
