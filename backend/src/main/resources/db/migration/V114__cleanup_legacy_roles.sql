-- V114: 清理旧角色体系残留（ADMIN/MANAGER/STAFF/auditor）
-- 
-- 背景：角色体系从旧 ADMIN/MANAGER/STAFF/auditor 升级到 8 种 RoleProfile。
-- 旧角色在 flyway_seed_data 和 role_profiles 中的残留，导致新角色用户在某些
-- 权限判断路径上匹配失败。
--
-- 不做：不删除 role_profiles 表中的旧角色行（因为 User.role_id 可能仍引用它们
-- 且 Flyway 种子数据中已不再插入旧角色），仅清理 flyway_seed_data 中的残留。
-- 
-- 用户数据迁移：User 表中旧 role 值（ADMIN/MANAGER/STAFF）本身是枚举字段，
-- 不会影响新 roleCode 的权限路由，保留不变。

-- Step 1: 删除 flyway_seed_data 中遗留的旧角色引用
DELETE FROM flyway_seed_data WHERE entity_type = 'RoleProfile' AND entity_code IN ('manager', 'staff', 'auditor');

-- Step 2: 清理 role_permissions 中孤立的旧角色权限（关联已不存在的旧角色）
DELETE rp FROM role_permissions rp
LEFT JOIN role_profiles rp2 ON rp.role_id = rp2.id
WHERE rp2.id IS NULL;
