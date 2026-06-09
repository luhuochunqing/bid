-- V1071__: Create account_borrow_applications table for borrow approval workflow
-- Gitee Issue IJTGNY: 账号借用归还流程缺失审批环节
-- P0 issue — new borrow approval workflow with status machine

CREATE TABLE IF NOT EXISTS account_borrow_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    account_id BIGINT NOT NULL COMMENT '借用的账号ID',
    applicant_id BIGINT NOT NULL COMMENT '申请人ID',
    custodian_id BIGINT NOT NULL COMMENT '账号保管员ID（审批人）',
    purpose VARCHAR(500) DEFAULT NULL COMMENT '使用目的',
    project_name VARCHAR(200) DEFAULT NULL COMMENT '关联项目',
    expected_return_at DATETIME DEFAULT NULL COMMENT '预计归还日期',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL' COMMENT '申请状态: PENDING_APPROVAL/APPROVED/REJECTED/RETURNED/CANCELLED',
    reject_reason VARCHAR(500) DEFAULT NULL COMMENT '拒绝原因',
    approved_at DATETIME DEFAULT NULL COMMENT '审批通过时间',
    returned_at DATETIME DEFAULT NULL COMMENT '实际归还时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_acct_borrow_applicant (applicant_id),
    INDEX idx_acct_borrow_account (account_id),
    INDEX idx_acct_borrow_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号借用申请表';
