-- V1160: platform_account_password_nullable
-- CO-567: 账户管理平台密码字段改为非必填，password 列允许 NULL（NULL 表示该账户无平台密码）

ALTER TABLE platform_accounts
    MODIFY COLUMN password VARCHAR(255) NULL;
