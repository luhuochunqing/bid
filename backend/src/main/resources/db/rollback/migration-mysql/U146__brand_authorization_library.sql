-- Input: V146__brand_authorization_library.sql
-- 品牌授权库回滚：删除品牌授权库相关表
-- Flyway rollback MySQL 8.0

DROP TABLE IF EXISTS brand_auth_alert_config;
DROP TABLE IF EXISTS brand_authorization;
