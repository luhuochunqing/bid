-- V1053: Add file_size column to performance_attachment table
-- §4.5.1 #1/5 业绩管理 — tracks attachment file size for display and validation

ALTER TABLE performance_attachment
    ADD COLUMN file_size BIGINT DEFAULT NULL COMMENT '文件大小(字节)';
