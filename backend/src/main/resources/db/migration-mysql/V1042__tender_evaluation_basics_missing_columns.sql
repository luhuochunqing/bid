-- V1042: 补齐 tender_evaluation_basics 缺失的列（rebase 后遗留）
-- 这些列在 rebase 前由 Hibernate ddl-auto=update 隐式创建，Flyway 链中缺失

-- 使用存储过程实现幂等
DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_not_exists$$
CREATE PROCEDURE add_column_if_not_exists(
    IN p_column_name VARCHAR(64),
    IN p_column_def VARCHAR(500)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'tender_evaluation_basics'
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @sql = CONCAT('ALTER TABLE tender_evaluation_basics',
            ' ADD COLUMN ', p_column_name, ' ', p_column_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL add_column_if_not_exists('planned_shortlisted_count', 'INT');
CALL add_column_if_not_exists('mro_office_flow_amount', 'DECIMAL(15,2)');
CALL add_column_if_not_exists('contingency_plan', 'VARCHAR(5000)');

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
