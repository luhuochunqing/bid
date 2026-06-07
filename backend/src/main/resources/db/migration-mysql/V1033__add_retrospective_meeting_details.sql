-- V1034: 添加项目复盘会议详情及附件字段
-- 适用: §3.3.1.5 项目复盘

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

DELIMITER ;

CALL drop_column_if_exists('project_retrospective', 'meeting_time');
CALL drop_column_if_exists('project_retrospective', 'meeting_type');
CALL drop_column_if_exists('project_retrospective', 'participants');
CALL drop_column_if_exists('project_retrospective', 'attachment_id');

DROP PROCEDURE IF EXISTS drop_column_if_exists;

ALTER TABLE project_retrospective ADD COLUMN meeting_time TIMESTAMP NULL;
ALTER TABLE project_retrospective ADD COLUMN meeting_type VARCHAR(32) NULL;
ALTER TABLE project_retrospective ADD COLUMN participants VARCHAR(1024) NULL;
ALTER TABLE project_retrospective ADD COLUMN attachment_id BIGINT NULL;
