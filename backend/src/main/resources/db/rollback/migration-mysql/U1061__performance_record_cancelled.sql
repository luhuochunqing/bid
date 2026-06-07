-- Input: V1061__performance_record_cancelled.sql
-- Rollback for V1061__performance_record_cancelled.sql

DROP INDEX idx_performance_cancelled ON performance_record;
ALTER TABLE performance_record
    DROP COLUMN cancelled,
    DROP COLUMN cancelled_at,
    DROP COLUMN cancelled_by,
    DROP COLUMN cancelled_reason;
