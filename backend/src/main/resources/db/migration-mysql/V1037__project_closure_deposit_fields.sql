-- V1034: 项目结项表新增保证金退回状态字段（蓝图 §3.3.1.6）
-- 新增：deposit_return_status, deposit_return_date, transfer_amount, returned_amount, rejection_reason

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

DROP PROCEDURE IF EXISTS run_v1034_migration$$
CREATE PROCEDURE run_v1034_migration()
BEGIN
    CALL add_column_if_not_exists('project_closure', 'deposit_return_status', "VARCHAR(32) DEFAULT 'NA'");
    CALL add_column_if_not_exists('project_closure', 'deposit_return_date', 'DATETIME');
    CALL add_column_if_not_exists('project_closure', 'transfer_amount', 'DECIMAL(18,2)');
    CALL add_column_if_not_exists('project_closure', 'returned_amount', 'DECIMAL(18,2)');
    CALL add_column_if_not_exists('project_closure', 'rejection_reason', 'TEXT');
    CALL add_index_if_not_exists('project_closure', 'idx_closure_review_status', 'ON project_closure(review_status)');
END$$

DELIMITER ;

CALL run_v1034_migration();
DROP PROCEDURE IF EXISTS run_v1034_migration;
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
