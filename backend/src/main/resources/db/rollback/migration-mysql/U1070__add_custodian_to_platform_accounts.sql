-- Input: V1070__add_custodian_to_platform_accounts.sql
-- U1070: Rollback - remove custodian field from platform_accounts table
-- Gitee Issue IJTGIO: 回滚时先判断列是否存在，安全移除

ALTER TABLE platform_accounts
  DROP COLUMN IF EXISTS custodian;
