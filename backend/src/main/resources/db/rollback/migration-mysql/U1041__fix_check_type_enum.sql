-- Input: V1041__fix_check_type_enum.sql
-- Rollback V1041: 恢复 compliance_check_results.check_type 为 VARCHAR(50)
ALTER TABLE compliance_check_results
    MODIFY COLUMN check_type VARCHAR(50) NOT NULL DEFAULT 'COMPLIANCE' COMMENT '检查类型: COMPLIANCE=合规检查, BID_DOCUMENT_QUALITY=标书文档质量核查';
