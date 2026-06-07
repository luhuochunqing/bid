-- V1017: Account borrow request tracking table
-- §5 #7/13 消息提醒 — foundation for borrow approval workflow and notifications
-- Tracks borrow request lifecycle: PENDING → APPROVED → RETURNED / REJECTED / CANCELLED

CREATE TABLE account_borrow_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL COMMENT 'FK to platform_accounts.id',
    borrower_id BIGINT NOT NULL COMMENT 'Borrower user ID',
    custodian_id BIGINT DEFAULT NULL COMMENT 'Account custodian user ID at time of request',
    purpose VARCHAR(500) DEFAULT NULL COMMENT 'Purpose of borrow',
    project_id BIGINT DEFAULT NULL COMMENT 'Associated project ID',
    expected_return_date DATE DEFAULT NULL COMMENT 'Expected return date',
    actual_return_date DATE DEFAULT NULL COMMENT 'Actual return date',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|APPROVED|REJECTED|RETURNED|CANCELLED',
    approver_id BIGINT DEFAULT NULL COMMENT 'Approving custodian user ID',
    approval_comment VARCHAR(1000) DEFAULT NULL COMMENT 'Approval comment or rejection reason',
    approved_at DATETIME DEFAULT NULL,
    returned_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_borrow_account (account_id),
    INDEX idx_borrow_borrower (borrower_id),
    INDEX idx_borrow_status (status),
    INDEX idx_borrow_expected (expected_return_date),
    CONSTRAINT fk_borrow_account FOREIGN KEY (account_id) REFERENCES platform_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
