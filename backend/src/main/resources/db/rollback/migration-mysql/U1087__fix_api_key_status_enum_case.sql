-- Input: V1087__fix_api_key_status_enum_case.sql
-- 回滚：恢复 api_keys.status 为小写枚举
ALTER TABLE api_keys MODIFY COLUMN status ENUM('active','disabled','expired') NOT NULL DEFAULT 'active';
