-- Nullable metadata only: historical scores are not recalculated or promoted by migration.
SET @estimate_fp_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
           AND table_name = 'score_item' AND column_name = 'estimate_input_fingerprint'),
    'SELECT 1', 'ALTER TABLE score_item ADD COLUMN estimate_input_fingerprint VARCHAR(64) NULL');
PREPARE estimate_fp_stmt FROM @estimate_fp_sql;
EXECUTE estimate_fp_stmt;
DEALLOCATE PREPARE estimate_fp_stmt;
SET @estimate_time_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
           AND table_name = 'score_item' AND column_name = 'estimated_at'),
    'SELECT 1', 'ALTER TABLE score_item ADD COLUMN estimated_at DATETIME(6) NULL');
PREPARE estimate_time_stmt FROM @estimate_time_sql;
EXECUTE estimate_time_stmt;
DEALLOCATE PREPARE estimate_time_stmt;
