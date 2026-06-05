-- 补充标讯通知公告字段
-- bid_notice / bid_notice_file_url 在 Tender 实体中已定义
-- 对应的 V110 迁移放在错误目录，此处补齐
-- 幂等：仅当列不存在时才添加

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

CALL add_column_if_not_exists('tenders', 'bid_notice',          'TEXT NULL COMMENT ''公告正文'' AFTER bid_opening_time');
CALL add_column_if_not_exists('tenders', 'bid_notice_file_url', 'VARCHAR(1000) NULL COMMENT ''公告附件 URL'' AFTER bid_notice');

DROP PROCEDURE IF EXISTS add_column_if_not_exists$$
