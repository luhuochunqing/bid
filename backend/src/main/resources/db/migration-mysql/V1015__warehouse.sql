-- V1015: Create warehouse table per blueprint §4.4 仓库信息

CREATE TABLE warehouse (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL COMMENT '仓库名称',
    type ENUM('SELF_OPERATED', 'CLOUD') NOT NULL COMMENT '自营/云仓',
    region VARCHAR(20) NOT NULL COMMENT '所属区域: 华东/华北/华南/西南/西北/东北/华中',
    province VARCHAR(50) NOT NULL COMMENT '所在省份',
    address VARCHAR(500) NOT NULL COMMENT '具体地址',
    area DECIMAL(10,2) NOT NULL COMMENT '仓库面积(㎡)',
    contact_person VARCHAR(100) NOT NULL COMMENT '区域联系人',
    remarks TEXT COMMENT '备注',

    start_date DATE NOT NULL COMMENT '开始时间',
    end_date DATE NOT NULL COMMENT '结束时间',
    lessor VARCHAR(200) NOT NULL COMMENT '出租方',
    lessee VARCHAR(200) NOT NULL COMMENT '承租方',
    invoice_period VARCHAR(100) COMMENT '最近发票租期',
    close_plan VARCHAR(500) COMMENT '关仓计划',

    has_property_cert TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否有产权证',
    has_invoice TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否有发票',
    has_photos TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否有内外照片',
    cert_remarks VARCHAR(500) COMMENT '资料核验备注',

    status VARCHAR(20) NOT NULL DEFAULT 'IN_USE' COMMENT '使用中/即将到期/已到期/已关仓',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_warehouse_status (status),
    INDEX idx_warehouse_region (region),
    INDEX idx_warehouse_end_date (end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库信息';
