-- V132__tender_assignment_record_type.sql
-- US2: 在分配记录表中添加 type 字段，区分分配（DISPATCH）与转派（TRANSFER）
-- FR-009 ~ FR-014

ALTER TABLE tender_assignment_records
ADD COLUMN type ENUM('DISPATCH', 'TRANSFER') NOT NULL DEFAULT 'DISPATCH'
COMMENT '记录类型: DISPATCH-分配, TRANSFER-转派'
AFTER remark;
