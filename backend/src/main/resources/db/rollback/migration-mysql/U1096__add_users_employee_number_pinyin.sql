-- Input: V1096__add_users_employee_number_pinyin.sql
-- Rollback for V1096__add_users_employee_number_pinyin.sql
-- 撤销 V1096：删除 employee_number_pinyin 列与索引，回退到 V1095 后的状态。
-- 注：回滚后 employee_number_pinyin 数据丢失；工号本身的拼音搜索能力随之失效。
-- 幂等：用 information_schema 前置判断列/索引是否存在。

-- 1. 删除索引（若存在）
DROP PROCEDURE IF EXISTS p_u1096_drop_idx_if_exists;
DELIMITER $$
CREATE PROCEDURE p_u1096_drop_idx_if_exists()
BEGIN
  IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND INDEX_NAME = 'idx_users_employee_number_pinyin'
  ) THEN
    ALTER TABLE users DROP INDEX idx_users_employee_number_pinyin;
  END IF;
END$$
DELIMITER ;

CALL p_u1096_drop_idx_if_exists();
DROP PROCEDURE IF EXISTS p_u1096_drop_idx_if_exists;

-- 2. 删除列（若存在）
DROP PROCEDURE IF EXISTS p_u1096_drop_col_if_exists;
DELIMITER $$
CREATE PROCEDURE p_u1096_drop_col_if_exists()
BEGIN
  IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'employee_number_pinyin'
  ) THEN
    ALTER TABLE users DROP COLUMN employee_number_pinyin;
  END IF;
END$$
DELIMITER ;

CALL p_u1096_drop_col_if_exists();
DROP PROCEDURE IF EXISTS p_u1096_drop_col_if_exists;
