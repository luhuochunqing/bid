-- Input: migration-mysql/V1160__platform_account_password_nullable.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1160: 回滚 platform_account_password_nullable（CO-567）
-- 将 password 列恢复为 NOT NULL；NULL 值先置空串避免 MODIFY 失败

UPDATE platform_accounts SET password = '' WHERE password IS NULL;
ALTER TABLE platform_accounts
    MODIFY COLUMN password VARCHAR(255) NOT NULL;
