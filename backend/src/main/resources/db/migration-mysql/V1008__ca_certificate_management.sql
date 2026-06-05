-- V1008: CA 信息管理 (Blueprint 5.3)
-- 创建 ca_certificates + ca_borrow_applications + ca_borrow_events 三张表

CREATE TABLE IF NOT EXISTS ca_certificates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform_ids VARCHAR(512) DEFAULT NULL COMMENT '关联投标平台ID列表，JSON数组格式',
    ca_type VARCHAR(20) NOT NULL COMMENT 'CA类型: ENTITY_CA(实体CA) / ELECTRONIC_CA(电子CA)',
    seal_type VARCHAR(30) NOT NULL COMMENT '印章类型: OFFICIAL_SEAL(公章) / LEGAL_PERSON_SEAL(法人章) / LEGAL_SIGN(法人签字) / CONTACT_SIGN(联系人签字)',
    electronic_account VARCHAR(100) DEFAULT NULL COMMENT '电子CA账号，实体CA时为NULL',
    ca_password VARCHAR(512) DEFAULT NULL COMMENT 'CA密码，加密存储',
    issuer VARCHAR(100) DEFAULT NULL COMMENT '颁发机构',
    holder_name VARCHAR(100) DEFAULT NULL COMMENT '持有人姓名',
    expiry_date DATE NOT NULL COMMENT 'CA有效期',
    ca_platform_url VARCHAR(500) DEFAULT NULL COMMENT 'CA平台/App/小程序入口',
    custodian_id BIGINT NOT NULL COMMENT 'CA保管员用户ID',
    custodian_name VARCHAR(100) NOT NULL COMMENT 'CA保管员姓名',
    borrow_status VARCHAR(30) NOT NULL DEFAULT 'IN_STOCK' COMMENT '借用状态: IN_STOCK(在库) / BORROWED(已借出) / OVERDUE(已逾期)',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT 'CA状态: ACTIVE(有效) / EXPIRING(即将到期) / EXPIRED(已过期) / INACTIVE(已下架)',
    remarks TEXT DEFAULT NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ca_custodian (custodian_id),
    INDEX idx_ca_borrow_status (borrow_status),
    INDEX idx_ca_status_expiry (status, expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CA数字证书表';

CREATE TABLE IF NOT EXISTS ca_borrow_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ca_certificate_id BIGINT NOT NULL COMMENT '关联CA证书ID',
    applicant_id BIGINT NOT NULL COMMENT '申请人用户ID',
    applicant_name VARCHAR(100) NOT NULL COMMENT '申请人姓名',
    purpose VARCHAR(500) NOT NULL COMMENT '使用目的',
    project_id BIGINT DEFAULT NULL COMMENT '关联项目ID',
    project_name VARCHAR(200) DEFAULT NULL COMMENT '关联项目名称',
    borrow_duration_type VARCHAR(20) NOT NULL COMMENT '借用期限: SHORT_TERM(短期) / LONG_TERM(长期)',
    expected_return_date DATE DEFAULT NULL COMMENT '预计归还日期，短期必填',
    commitment_letter_url VARCHAR(500) DEFAULT NULL COMMENT '盖章承诺书附件URL，长期必填',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL' COMMENT '状态: PENDING_APPROVAL(待审批) / APPROVED(已通过) / REJECTED(已拒绝) / RETURNED(已归还) / CANCELLED(已取消)',
    approver_id BIGINT DEFAULT NULL COMMENT '审批人用户ID',
    approver_name VARCHAR(100) DEFAULT NULL COMMENT '审批人姓名',
    approval_comment VARCHAR(500) DEFAULT NULL COMMENT '审批意见/拒绝原因',
    approved_at DATETIME DEFAULT NULL COMMENT '审批时间',
    actual_return_date DATE DEFAULT NULL COMMENT '实际归还日期',
    return_notes VARCHAR(500) DEFAULT NULL COMMENT '归还备注',
    returned_at DATETIME DEFAULT NULL COMMENT '归还登记时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ca_borrow_cert (ca_certificate_id),
    INDEX idx_ca_borrow_applicant (applicant_id),
    INDEX idx_ca_borrow_status (status),
    INDEX idx_ca_borrow_approver (approver_id),
    CONSTRAINT fk_ca_borrow_cert FOREIGN KEY (ca_certificate_id) REFERENCES ca_certificates(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CA借用申请表';

CREATE TABLE IF NOT EXISTS ca_borrow_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL COMMENT '关联借用申请ID',
    event_type VARCHAR(30) NOT NULL COMMENT '事件类型: SUBMITTED/APPROVED/REJECTED/RETURNED/CANCELLED',
    actor_id BIGINT NOT NULL COMMENT '操作人用户ID',
    actor_name VARCHAR(100) NOT NULL COMMENT '操作人姓名',
    comment VARCHAR(500) DEFAULT NULL COMMENT '操作备注',
    status_before VARCHAR(30) DEFAULT NULL COMMENT '变更前状态',
    status_after VARCHAR(30) NOT NULL COMMENT '变更后状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ca_event_app (application_id),
    INDEX idx_ca_event_actor (actor_id),
    CONSTRAINT fk_ca_event_app FOREIGN KEY (application_id) REFERENCES ca_borrow_applications(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CA借用事件溯源表';
