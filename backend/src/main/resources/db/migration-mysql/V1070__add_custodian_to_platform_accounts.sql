-- V1070: Add custodian field to platform_accounts table
-- Gitee Issue IJTGIO: 标讯平台账号管理缺少账号保管员字段(custodian)
-- §5 - 招标平台账号管理 - 账号保管员字段

ALTER TABLE platform_accounts
  ADD COLUMN custodian BIGINT DEFAULT NULL COMMENT '账号保管员用户ID' AFTER ca_custodian;
