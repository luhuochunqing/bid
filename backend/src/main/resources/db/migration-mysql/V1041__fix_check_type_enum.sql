-- V1041: 修复 compliance_check_results.check_type 列类型
-- V1040 创建 check_type 为 VARCHAR(50)，但 JPA 实体 @Enumerated(EnumType.STRING) 需要 ENUM 类型
-- 迁移 V1040 已正确创建列和索引，本迁移仅修改列类型，不重建索引

ALTER TABLE compliance_check_results
    MODIFY COLUMN check_type ENUM('COMPLIANCE', 'BID_DOCUMENT_QUALITY') NOT NULL DEFAULT 'COMPLIANCE' COMMENT '检查类型: COMPLIANCE=合规检查, BID_DOCUMENT_QUALITY=标书文档质量核查';
