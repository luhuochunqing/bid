-- U1056: Remove cancelled columns from performance_record

DROP INDEX idx_performance_cancelled ON performance_record;

ALTER TABLE performance_record
    DROP COLUMN cancelled,
    DROP COLUMN cancelled_at,
    DROP COLUMN cancelled_by,
    DROP COLUMN cancel_reason;
