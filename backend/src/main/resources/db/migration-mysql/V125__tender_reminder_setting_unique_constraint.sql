-- 强制约束：tender_id + reminder_type 联合唯一
-- 防止并发双击/重复订阅导致重复提醒设置记录
-- 同时优化查询：findByTenderIdAndReminderType 可命中覆盖索引
-- 幂等：仅当约束不存在时才创建

DELIMITER $$
DROP PROCEDURE IF EXISTS add_unique_constraint_if_not_exists$$
CREATE PROCEDURE add_unique_constraint_if_not_exists()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'tender_reminder_settings'
          AND INDEX_NAME = 'uk_tender_reminder_tender_type'
          AND NON_UNIQUE = 0
    ) THEN
        ALTER TABLE tender_reminder_settings
            ADD CONSTRAINT uk_tender_reminder_tender_type
            UNIQUE (tender_id, reminder_type);
    END IF;
END$$
DELIMITER ;

CALL add_unique_constraint_if_not_exists();
DROP PROCEDURE IF EXISTS add_unique_constraint_if_not_exists$$
