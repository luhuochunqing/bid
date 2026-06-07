-- Input: V145__performance_library.sql
-- 业绩库回滚：删除业绩库相关表
-- Flyway rollback MySQL 8.0

DROP TABLE IF EXISTS performance_attachment;
DROP TABLE IF EXISTS performance_record;
