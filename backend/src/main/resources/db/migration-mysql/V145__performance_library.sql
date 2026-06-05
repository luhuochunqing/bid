-- V145__performance_library.sql
-- 业绩库：历史中标/合同台账，供标书编制时一键引用
-- Flyway migration MySQL 8.0

CREATE TABLE performance_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_name VARCHAR(200) NOT NULL COMMENT '项目名称',
    bidding_entity VARCHAR(200) COMMENT '招标主体/甲方',
    contract_amount DECIMAL(15,2) COMMENT '合同金额(万元)',
    contract_date DATE COMMENT '合同签订日期',
    industry_category VARCHAR(50) COMMENT '所属行业',
    project_manager_id BIGINT COMMENT '项目经理ID',
    project_manager_name VARCHAR(100) COMMENT '项目经理姓名',
    status VARCHAR(20) NOT NULL DEFAULT 'SIGNED' COMMENT '合同状态: SIGNED/EXECUTING/COMPLETED/TERMINATED',
    bid_win_notice_url VARCHAR(500) COMMENT '中标通知书URL',
    contract_scan_url VARCHAR(500) COMMENT '合同扫描件URL(脱敏)',
    summary TEXT COMMENT '业绩摘要/亮点描述',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    INDEX idx_perf_industry (industry_category),
    INDEX idx_perf_manager (project_manager_id),
    INDEX idx_perf_amount (contract_amount),
    INDEX idx_perf_status (status),
    INDEX idx_perf_date (contract_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业绩记录';

CREATE TABLE performance_attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    performance_id BIGINT NOT NULL COMMENT '业绩ID',
    file_name VARCHAR(200) NOT NULL COMMENT '文件名',
    file_url VARCHAR(500) NOT NULL COMMENT '文件URL',
    file_type VARCHAR(50) COMMENT '文件类型: BID_WIN_NOTICE/CONTRACT_SCAN/OTHER',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (performance_id) REFERENCES performance_record(id) ON DELETE CASCADE,
    INDEX idx_attachment_perf (performance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业绩附件';
