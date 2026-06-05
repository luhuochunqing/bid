-- ================================================================
-- V1006: 标讯创建表单字段补全（对齐蓝图 2.3.1）
-- 功能：为 tenders 表新增 tender_info / source_platform / crm_opportunity_id 字段
-- 背景：蓝图 2.3.1 基本信息表单新增"标讯信息"、"来源平台"、"CRM商机"字段
-- 幂等：仅当列不存在时才添加（使用存储过程）
-- ================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_not_exists$$
CREATE PROCEDURE add_column_if_not_exists(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_def VARCHAR(1000)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table_name, ' ADD COLUMN ', p_column_name, ' ', p_column_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- 新增字段
CALL add_column_if_not_exists('tenders', 'tender_info', 'VARCHAR(5000) NULL COMMENT ''标讯信息''');
CALL add_column_if_not_exists('tenders', 'source_platform', 'VARCHAR(100) NULL COMMENT ''来源平台：人工录入/中国政府采购网/各省招标网/第三方商机服务/企业招标平台''');
CALL add_column_if_not_exists('tenders', 'crm_opportunity_id', 'BIGINT NULL COMMENT ''CRM商机ID，跟踪中状态后必填''');

-- 索引优化（crm_opportunity_id 查询）
SET @index_sql = 'CREATE INDEX idx_tender_crm_opportunity_id ON tenders (crm_opportunity_id)';
SET @exists = (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'tenders'
      AND INDEX_NAME = 'idx_tender_crm_opportunity_id'
    LIMIT 1
);
SET @sql = IF(@exists IS NULL, @index_sql, 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP PROCEDURE IF EXISTS add_column_if_not_exists$$
