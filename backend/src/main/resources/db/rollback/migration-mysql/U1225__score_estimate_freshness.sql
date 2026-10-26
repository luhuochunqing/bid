-- Input: migration-mysql/V1225__score_estimate_freshness.sql
-- Output: removes derived estimate metadata while preserving rule and score columns.
-- Pos: Flyway rollback for certificate estimate freshness metadata.
SET @estimate_time_undo = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
           AND table_name = 'score_item' AND column_name = 'estimated_at'),
    'ALTER TABLE score_item DROP COLUMN estimated_at', 'SELECT 1');
PREPARE estimate_time_undo_stmt FROM @estimate_time_undo;
EXECUTE estimate_time_undo_stmt;
DEALLOCATE PREPARE estimate_time_undo_stmt;
SET @estimate_fp_undo = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
           AND table_name = 'score_item' AND column_name = 'estimate_input_fingerprint'),
    'ALTER TABLE score_item DROP COLUMN estimate_input_fingerprint', 'SELECT 1');
PREPARE estimate_fp_undo_stmt FROM @estimate_fp_undo;
EXECUTE estimate_fp_undo_stmt;
DEALLOCATE PREPARE estimate_fp_undo_stmt;
