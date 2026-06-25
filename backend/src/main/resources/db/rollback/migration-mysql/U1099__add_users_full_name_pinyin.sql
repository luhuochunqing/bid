-- Input: V1099__add_users_full_name_pinyin.sql
-- Rollback for V1099__add_users_full_name_pinyin.sql
-- 撤销 V1099：删除 full_name_pinyin 索引和列，回退到 V1098 后的状态。
-- 注：回滚后 full_name_pinyin 数据丢失，需重新跑 V1099 + UserNamePinyinBackfillRunner 才能恢复。
-- 幂等：用 information_schema 前置判断。

-- 1. 删除索引（若存在）
DROP PROCEDURE IF EXISTS p_u1099_drop_idx_if_exists;
DELIMITER $$
CREATE PROCEDURE p_u1099_drop_idx_if_exists()
BEGIN
  IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND INDEX_NAME = 'idx_users_full_name_pinyin'
  ) THEN
    ALTER TABLE users DROP INDEX idx_users_full_name_pinyin;
  END IF;
END$$
DELIMITER ;

CALL p_u1099_drop_idx_if_exists();
DROP PROCEDURE IF EXISTS p_u1099_drop_idx_if_exists;

-- 2. 删除列（若存在）
DROP PROCEDURE IF EXISTS p_u1099_drop_col_if_exists;
DELIMITER $$
CREATE PROCEDURE p_u1099_drop_col_if_exists()
BEGIN
  IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'full_name_pinyin'
  ) THEN
    ALTER TABLE users DROP COLUMN full_name_pinyin;
  END IF;
END$$
DELIMITER ;

CALL p_u1099_drop_col_if_exists();
DROP PROCEDURE IF EXISTS p_u1099_drop_col_if_exists;
