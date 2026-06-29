-- Input: V1108__platform_account_contact_person_userid.sql
-- U1108: Rollback - 恢复 contact_person 为 VARCHAR(200) + 恢复 custodian / ca_custodian 列
-- 关联: CO-390
-- 注意: 回滚后 contact_person / custodian / ca_custodian 历史数据已丢失（V1108 已 DROP），仅恢复列结构

-- Step 1: 恢复 contact_person 为 VARCHAR(200)（丢弃 userId 值）
ALTER TABLE platform_accounts
  DROP COLUMN contact_person,
  ADD COLUMN contact_person VARCHAR(200) DEFAULT NULL COMMENT '绑定联系人 建议格式: 姓名(工号)' AFTER url;

-- Step 2: 恢复 ca_custodian 列（位置与 V1018 原始定义一致：在 has_ca 之后）
ALTER TABLE platform_accounts
  ADD COLUMN ca_custodian BIGINT DEFAULT NULL COMMENT 'CA保管人用户ID' AFTER has_ca;

-- Step 3: 恢复 custodian 列（位置与 V1070 原始定义一致：在 ca_custodian 之后）
ALTER TABLE platform_accounts
  ADD COLUMN custodian BIGINT DEFAULT NULL COMMENT '账号保管员用户ID' AFTER ca_custodian;
