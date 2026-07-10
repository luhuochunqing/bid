-- 安全修复：将 OSS 同步用户的本地密码全部置为锁定哈希
-- 根因：OrganizationUserSyncWriter 曾为 OSS 新用户写入测试密码 123456 的 BCrypt 编码，
-- 且 AuthService 在 OSS 认证失败时允许本地密码回退，导致所有 OSS 用户可用 123456 登录。
-- 修复后 OSS 用户必须走西域 OSS 统一认证，本地密码验证永远失败。
--
-- 注意：本地账号（external_org_source_app 为空）不受影响；DefaultAdminInitializer / LocalDevAccountInitializer
-- 创建的本地账号保持原有密码。

UPDATE users
SET password = '$2a$10$7EqJtq98hPqEX7fNZaFWoOHIhi4YhML26vP7Hk1UR93E1Vda8yI9W'
WHERE external_org_source_app IS NOT NULL
  AND external_org_source_app != '';
