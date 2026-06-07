-- U1055: Remove soft delete columns from performance_attachment

DROP INDEX idx_attachment_deleted ON performance_attachment;

ALTER TABLE performance_attachment
    DROP COLUMN is_deleted,
    DROP COLUMN deleted_at,
    DROP COLUMN deleted_by;
