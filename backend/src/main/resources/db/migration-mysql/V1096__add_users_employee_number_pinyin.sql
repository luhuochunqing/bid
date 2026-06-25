-- Add employee_number_pinyin column to users table (idempotent)
SET @preparedStatement = (SELECT IF(
    (
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE table_schema = DATABASE()
          AND table_name = 'users'
          AND column_name = 'employee_number_pinyin'
    ) > 0,
    'SELECT 1',
    'ALTER TABLE users ADD COLUMN employee_number_pinyin VARCHAR(255) NULL COMMENT ''工号拼音'' AFTER full_name_pinyin'
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
