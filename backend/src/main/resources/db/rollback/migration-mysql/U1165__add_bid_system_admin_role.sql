-- Input: V1165__add_bid_system_admin_role.sql
-- U1165: 回滚 V1165 — 删除 bid-SystemAdmin 角色
-- Manual rollback required: 回滚前需确认没有 users 表记录关联到此角色码。
-- 回滚后，OSS 端 bid-SystemAdmin 角色码将无法解析（mapOssRoleCodeToInternal 返回 null），
-- 相关用户会 fail-closed 无法登录。

DELETE FROM roles WHERE code = 'bid-SystemAdmin';
