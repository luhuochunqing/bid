-- U1159: 回滚 V1159 — 恢复 uk_roles_code 唯一索引
-- Input: V1159__drop_duplicate_roles_code_index.sql
-- 注意：回滚会恢复 V1158 新增的 uk_roles_code 索引，与 B73 基线的 UK_ch1113horj4qr56f91omojv8 重复。
--       仅在需要回退到 V1158 状态时使用。

ALTER TABLE roles ADD UNIQUE INDEX uk_roles_code (code);