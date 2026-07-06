-- Input: V1140__platform_account_username_scoped_unique.sql
-- U1140: Rollback - drop composite unique constraint and restore global unique on username

ALTER TABLE platform_accounts
  DROP CONSTRAINT uk_platform_accounts_platform_type_username;

ALTER TABLE platform_accounts
  ADD CONSTRAINT UK_14e3v80s7wywpcjk713rniikd UNIQUE (username);
