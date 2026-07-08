-- Input: V1154__drop_unique_constraint_from_platform_account_name.sql
-- U1154: Rollback - re-add uk_platform_accounts_account_name unique constraint

ALTER TABLE platform_accounts
  ADD CONSTRAINT uk_platform_accounts_account_name UNIQUE (account_name);
