-- Input: V1060__performance_attachment_soft_delete.sql
-- Rollback for V1060__performance_attachment_soft_delete.sql

DROP INDEX idx_attachment_deleted ON performance_attachment;
ALTER TABLE performance_attachment
    DROP COLUMN deleted,
    DROP COLUMN deleted_at,
    DROP COLUMN deleted_by;
