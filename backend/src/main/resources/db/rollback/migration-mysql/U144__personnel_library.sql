-- Input: V144__personnel_library.sql
-- 人员库回滚：删除人员库相关表
-- Flyway rollback MySQL 8.0

DROP TABLE IF EXISTS personnel_alert_config;
DROP TABLE IF EXISTS personnel_certificate;
DROP TABLE IF EXISTS personnel;
