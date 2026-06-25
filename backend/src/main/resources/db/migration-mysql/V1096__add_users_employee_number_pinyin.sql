-- Add employee_number_pinyin column to users table (idempotent)
-- NOTE: 原脚本使用 `AFTER full_name_pinyin`，但 full_name_pinyin 列在更早的 schema
-- 重置中被移除（PR #1088 清理拼音逻辑），导致 Flyway 执行报
-- "Unknown column 'full_name_pinyin'"，应用启动失败（主 DB 卡在 V1095）。
-- 改为 `AFTER employee_number`（V1095 保证该列存在）。主 DB 从未成功执行 V1096，
-- flyway_schema_history 无该版本记录，故改 checksum 不会触发本地 mismatch。
-- 若其他环境曾跑过原版 V1096，需执行 `flyway repair` 校正 checksum。
SET @preparedStatement = (SELECT IF(
    (
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE table_schema = DATABASE()
          AND table_name = 'users'
          AND column_name = 'employee_number_pinyin'
    ) > 0,
    'SELECT 1',
    'ALTER TABLE users ADD COLUMN employee_number_pinyin VARCHAR(255) NULL COMMENT ''工号拼音'' AFTER employee_number'
));
PREPARE addColumn FROM @preparedStatement;
EXECUTE addColumn;
DEALLOCATE PREPARE addColumn;

-- Backfill existing employee numbers (basic pinyin conversion for common digits/chars)
-- Note: Full backfill requires Java PinyinUtils; this covers common cases
UPDATE users 
SET employee_number_pinyin = employee_number 
WHERE employee_number_pinyin IS NULL 
  AND employee_number IS NOT NULL 
  AND employee_number != '';
