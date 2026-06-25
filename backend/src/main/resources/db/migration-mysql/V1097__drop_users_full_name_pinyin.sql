-- Migration V1097: drop full_name_pinyin column from users table.
-- Background: PR #1088 removed PinyinUtils + User.fullNamePinyin field + V1096 migration file,
-- but the column still exists in production databases (V1096 was previously applied).
-- This migration cleans up the orphaned column to keep schema in sync with code.
--
-- Idempotent pattern: uses information_schema check before ALTER TABLE,
-- so re-running this migration is safe even if the column was already dropped.

DROP PROCEDURE IF EXISTS p_v1097_drop_col_if_exists;
DELIMITER $$
CREATE PROCEDURE p_v1097_drop_col_if_exists()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
          AND COLUMN_NAME = 'full_name_pinyin'
    ) THEN
        ALTER TABLE users DROP COLUMN full_name_pinyin;
    END IF;
END$$
DELIMITER ;

CALL p_v1097_drop_col_if_exists();
DROP PROCEDURE IF EXISTS p_v1097_drop_col_if_exists;
