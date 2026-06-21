-- 修复 api_keys.status 枚举大小写与 Java ApiKeyStatus 不一致的问题
-- V135 使用小写 'active'/'disabled'/'expired'，Java 枚举为大写 ACTIVE/DISABLED/EXPIRED
UPDATE api_keys SET status = UPPER(status) WHERE status IS NOT NULL;
ALTER TABLE api_keys MODIFY COLUMN status ENUM('ACTIVE','DISABLED','EXPIRED') NOT NULL DEFAULT 'ACTIVE';
