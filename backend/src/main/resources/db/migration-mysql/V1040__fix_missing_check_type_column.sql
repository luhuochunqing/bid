-- V1040: 在 compliance_check_results 表中添加 check_type 列（补齐缺失的迁移）
-- V1041 依赖此列存在，但 V1040 迁移文件在仓库中缺失。本文件补齐此间隙。

SET @db_name = DATABASE();

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'compliance_check_results' AND COLUMN_NAME = 'check_type');

SET @sql = IF(@exists = 0,
    'ALTER TABLE compliance_check_results ADD COLUMN check_type VARCHAR(50) NOT NULL DEFAULT ''COMPLIANCE'' COMMENT ''检查类型: COMPLIANCE=合规检查, BID_DOCUMENT_QUALITY=标书文档质量核查'' AFTER overall_status',
    'SELECT ''check_type column already exists'' AS status');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
