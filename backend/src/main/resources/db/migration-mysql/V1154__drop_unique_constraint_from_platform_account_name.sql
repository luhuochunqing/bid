-- V1154__drop_unique_constraint_from_platform_account_name.sql
-- CO-559: 移除平台名称唯一约束，允许同一平台名称注册多个账户
-- 背景：V1072 添加了 account_name 唯一约束，但实际业务中一个平台可能有多个账户注册，
--       不应限制平台名称重复。

ALTER TABLE platform_accounts
  DROP INDEX uk_platform_accounts_account_name;
