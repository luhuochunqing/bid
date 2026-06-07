-- Input: V1062__performance_record_soft_delete.sql
-- Rollback for V1062__performance_record_soft_delete.sql

DROP INDEX idx_perf_deleted ON performance_record;
ALTER TABLE performance_record
    DROP COLUMN deleted,
    DROP COLUMN deleted_at,
    DROP COLUMN deleted_by;
