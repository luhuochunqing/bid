-- Rollback for V1097: re-add full_name_pinyin column to users table.
-- Note: This only restores the column schema; historical pinyin data is lost after V1097.
-- PinyinUtils.java was deleted in PR #1088, so the column will remain empty unless backfilled.

DROP PROCEDURE IF EXISTS p_u1097_add_col_if_missing;
DELIMITER $$
CREATE PROCEDURE p_u1097_add_col_if_missing()
BEGIN
    DECLARE col_count INT;
    SELECT COUNT(*) INTO col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'full_name_pinyin';
    IF col_count = 0 THEN
        ALTER TABLE users
            ADD COLUMN full_name_pinyin VARCHAR(255) DEFAULT NULL
            COMMENT '姓名拼音（全拼小写，空格分隔，用于搜索）'
            AFTER full_name;
    END IF;
END$$
DELIMITER ;

CALL p_u1097_add_col_if_missing();
DROP PROCEDURE IF EXISTS p_u1097_add_col_if_missing;
