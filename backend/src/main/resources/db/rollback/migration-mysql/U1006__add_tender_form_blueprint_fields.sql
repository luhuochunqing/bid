-- Input: V1006__add_tender_form_blueprint_fields.sql
-- Rollback for V1006__add_tender_form_blueprint_fields.sql

DELIMITER $$

DROP PROCEDURE IF EXISTS drop_column_if_exists$$
CREATE PROCEDURE drop_column_if_exists(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table_name, ' DROP COLUMN ', p_column_name);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS drop_index_if_exists$$
CREATE PROCEDURE drop_index_if_exists(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND INDEX_NAME = p_index_name
    ) THEN
        SET @sql = CONCAT('DROP INDEX ', p_index_name, ' ON ', p_table_name);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL drop_column_if_exists('tenders', 'tender_info');
CALL drop_column_if_exists('tenders', 'source_platform');
CALL drop_column_if_exists('tenders', 'crm_opportunity_id');
CALL drop_index_if_exists('tenders', 'idx_tender_crm_opportunity_id');

DROP PROCEDURE IF EXISTS drop_column_if_exists$$
DROP PROCEDURE IF EXISTS drop_index_if_exists$$
