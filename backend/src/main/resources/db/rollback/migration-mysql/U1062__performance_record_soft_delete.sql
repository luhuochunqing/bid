-- U1057: Remove soft delete columns from performance_record

DROP INDEX idx_perf_deleted ON performance_record;

ALTER TABLE performance_record
    DROP COLUMN is_deleted,
    DROP COLUMN deleted_at,
    DROP COLUMN deleted_by;
