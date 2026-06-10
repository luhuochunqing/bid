-- Input: V1072__add_unique_constraint_to_platform_account_name.sql
-- U1072: Rollback - drop uk_platform_accounts_account_name constraint

ALTER TABLE platform_accounts
  DROP CONSTRAINT uk_platform_accounts_account_name;
