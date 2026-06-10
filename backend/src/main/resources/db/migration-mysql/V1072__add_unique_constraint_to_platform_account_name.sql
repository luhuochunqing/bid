-- V1072__add_unique_constraint_to_platform_account_name.sql
-- IJTHNN 修复：投标平台名称 account_name 添加唯一约束
-- 蓝图 V1.0 第五部分「招标平台账号管理 → 新增账号」要求：
--   投标平台名称 → 唯一约束，不允许重名
-- 历史上 username 已是 UNIQUE；现补 account_name 唯一约束。

-- 1. 清理已存在的重名记录（保留最早创建的那条）
DELETE a1 FROM platform_accounts a1
INNER JOIN platform_accounts a2
  ON a1.account_name = a2.account_name
 AND a1.id > a2.id;

-- 2. 添加唯一约束
ALTER TABLE platform_accounts
  ADD CONSTRAINT uk_platform_accounts_account_name UNIQUE (account_name);
