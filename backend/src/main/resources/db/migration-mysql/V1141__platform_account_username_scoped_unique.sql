-- V1140__platform_account_username_scoped_unique.sql
-- CO-521: 平台账号 username 唯一性从全局唯一改为按 platform_type 唯一。
-- 不同 platform_type 允许相同 username。

-- 1. 清理同一 platform_type + username 下的重复记录，保留最早创建的一条
DELETE a1 FROM platform_accounts a1
INNER JOIN platform_accounts a2
  ON a1.platform_type = a2.platform_type
 AND a1.username = a2.username
 AND a1.id > a2.id;

-- 2. 删除旧的 username 全局唯一约束
ALTER TABLE platform_accounts
  DROP INDEX UK_14e3v80s7wywpcjk713rniikd;

-- 3. 新增 (platform_type, username) 组合唯一约束
ALTER TABLE platform_accounts
  ADD CONSTRAINT uk_platform_accounts_platform_type_username
    UNIQUE (platform_type, username);
