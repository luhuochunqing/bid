-- Input: V1142__remove_platform_type_from_platform_accounts.sql
-- U1142__remove_platform_type_from_platform_accounts.sql
-- 回滚：重新添加 platform_type 字段及组合唯一约束。
-- 注意：若回滚前已存在相同 username 的多条记录，添加唯一约束会失败。

ALTER TABLE platform_accounts
  ADD COLUMN platform_type VARCHAR(50) NOT NULL DEFAULT 'OTHER';

ALTER TABLE platform_accounts
  ADD INDEX idx_platform_type (platform_type);

ALTER TABLE platform_accounts
  ADD CONSTRAINT uk_platform_accounts_platform_type_username
    UNIQUE (platform_type, username);
