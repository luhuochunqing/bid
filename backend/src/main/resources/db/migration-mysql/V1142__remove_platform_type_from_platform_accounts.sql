-- V1142__remove_platform_type_from_platform_accounts.sql
-- 需求变更：删除 platform_type 字段，取消平台账号按平台类型的重复校验。

-- 1. 删除组合唯一约束
ALTER TABLE platform_accounts
  DROP INDEX uk_platform_accounts_platform_type_username;

-- 2. 删除 platform_type 索引
ALTER TABLE platform_accounts
  DROP INDEX idx_platform_type;

-- 3. 删除 platform_type 列
ALTER TABLE platform_accounts
  DROP COLUMN platform_type;
