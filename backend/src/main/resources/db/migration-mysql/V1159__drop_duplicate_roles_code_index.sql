-- V1159: 删除 roles.code 重复唯一索引 uk_roles_code
-- 背景：B73 基线已通过 ALTER TABLE 添加 UK_ch1113horj4qr56f91omojv8 unique (code)，
--       V1158 再次添加 uk_roles_code，导致同一列存在两个功能相同的 UNIQUE 索引。
--       MySQL 8.0 对此发出警告：Duplicate index 'uk_roles_code'，未来版本将禁止。
-- 修复：删除 V1158 新增的 uk_roles_code，保留 B73 基线的 UK_ch1113horj4qr56f91omojv8。
-- 幂等：V1159 仅在 V1158 之后执行，而 V1158 已创建 uk_roles_code，故索引必然存在。

ALTER TABLE roles DROP INDEX uk_roles_code;