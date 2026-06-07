-- Input: V1058__performance_attachment_file_size.sql
-- Rollback for V1058__performance_attachment_file_size.sql

ALTER TABLE performance_attachment
    DROP COLUMN file_size;
