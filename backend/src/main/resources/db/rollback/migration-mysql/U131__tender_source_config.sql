-- Input: migration-mysql/V131__tender_source_config.sql
-- Output: rollback script for mysql environments
-- Rollback V131: 删除 tender_source_configs 表

DROP TABLE IF EXISTS tender_source_configs;
