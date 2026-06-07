-- 添加标讯来源类型字段
-- MANUAL: 人工录入
-- EXTERNAL: 外部获取
-- 幂等：ADD COLUMN / CREATE INDEX 改为存储过程幂等判断

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

DROP PROCEDURE IF EXISTS add_index_if_not_exists$$
CREATE PROCEDURE add_index_if_not_exists(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_index_def VARCHAR(1000)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND INDEX_NAME = p_index_name
    ) THEN
        SET @sql = CONCAT('CREATE INDEX ', p_index_name, ' ', p_index_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL add_column_if_not_exists('tenders', 'source_type', "ENUM('MANUAL', 'EXTERNAL') DEFAULT 'MANUAL' COMMENT '标讯来源类型: MANUAL-人工录入, EXTERNAL-外部获取'");
CALL add_index_if_not_exists('tenders', 'idx_tender_source_type', 'ON tenders (source_type)');

-- 更新现有数据：source 字段为 'manual' 的设为 MANUAL，其他设为 EXTERNAL
UPDATE tenders SET source_type = 'MANUAL' WHERE source = 'manual';
UPDATE tenders SET source_type = 'EXTERNAL' WHERE source_type IS NULL OR source_type = '';

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
