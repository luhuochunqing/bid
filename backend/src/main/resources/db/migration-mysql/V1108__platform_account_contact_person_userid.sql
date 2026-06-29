-- V1107: 升级 platform_accounts.contact_person 为 userId(BIGINT) + 物理删除 custodian / ca_custodian 冗余列
-- 关联: CO-390 [资源-账户管理] contactPerson 字段升级 userId + custodian/caCustodian 物理删除
-- 背景: 业务合并"账户保管员 = 绑定联系人"，contactPerson 从字符串("姓名（工号）")升级为 userId
--       custodian / ca_custodian 与 contactPerson 业务上等价，物理删除以收口单字段
-- 历史数据策略: 不回填（业务确认），旧字符串数据直接丢弃；如需保留旧值，请在执行前手工导出

-- Step 1: DROP 旧 contact_person VARCHAR(200) + ADD 新 contact_person BIGINT（位置保持不变）
ALTER TABLE platform_accounts
  DROP COLUMN contact_person,
  ADD COLUMN contact_person BIGINT DEFAULT NULL COMMENT '绑定联系人 userId' AFTER url;

-- Step 2: 物理删除冗余字段 custodian（账号保管员，业务上 = 联系人）
ALTER TABLE platform_accounts
  DROP COLUMN custodian;

-- Step 3: 物理删除冗余字段 ca_custodian（CA 保管人，业务上 = 联系人）
ALTER TABLE platform_accounts
  DROP COLUMN ca_custodian;
