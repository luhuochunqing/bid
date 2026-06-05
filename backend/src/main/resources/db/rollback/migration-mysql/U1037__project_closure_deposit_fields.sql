-- Input: V1037__project_closure_deposit_fields.sql
-- Rollback for V1037__project_closure_deposit_fields.sql
-- U1034: 回滚 V1034 — 删除项目结项表新增字段

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

DROP PROCEDURE IF EXISTS run_u1034_rollback$$
CREATE PROCEDURE run_u1034_rollback()
BEGIN
    CALL drop_column_if_exists('project_closure', 'deposit_return_status');
    CALL drop_column_if_exists('project_closure', 'deposit_return_date');
    CALL drop_column_if_exists('project_closure', 'transfer_amount');
    CALL drop_column_if_exists('project_closure', 'returned_amount');
    CALL drop_column_if_exists('project_closure', 'rejection_reason');
    -- idx_closure_review_status 会随列删除自动清理
END$$

DELIMITER ;

CALL run_u1034_rollback();
DROP PROCEDURE IF EXISTS run_u1034_rollback;
DROP PROCEDURE IF EXISTS drop_column_if_exists;
