-- Purpose: 移除系统遗留的 staff 角色。
--  - 禁用当前 role_profile/role 为 staff 的全部用户（普通员工不应登录系统）。
--  - 清除这些用户的 role_id，role 列临时占位为 MANAGER（列非空且新的 Java Role 枚举已删除 STAFF）。
--  - 删除 roles 表中的 staff 角色定义。
--  - 收窄 users.role 枚举以匹配新的 User.Role 枚举（ADMIN, MANAGER）。

-- 1. 备份将被迁移的 staff 用户，便于回滚时恢复。
CREATE TABLE IF NOT EXISTS V1091_staff_users_backup (
    user_id BIGINT PRIMARY KEY,
    original_role_id BIGINT,
    original_role ENUM('ADMIN', 'MANAGER', 'STAFF') NOT NULL,
    original_enabled BIT NOT NULL,
    migrated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

INSERT INTO V1091_staff_users_backup (user_id, original_role_id, original_role, original_enabled)
SELECT id, role_id, role, enabled
FROM users
WHERE role = 'STAFF'
   OR role_id IN (SELECT id FROM roles WHERE LOWER(code) = 'staff');

-- 2. 禁用 staff 用户并清除角色关联。
--    role 列临时改为 MANAGER：用户已被禁用，不会获得 MANAGER 权限；此占位仅满足非空约束。
UPDATE users
SET enabled = false,
    role_id = NULL,
    role = 'MANAGER'
WHERE role = 'STAFF'
   OR role_id IN (SELECT id FROM roles WHERE LOWER(code) = 'staff');

-- 3. 删除 staff 角色定义。
DELETE FROM roles WHERE LOWER(code) = 'staff';

-- 4. 收窄 users.role 枚举，与 Java User.Role 枚举保持一致。
ALTER TABLE users MODIFY COLUMN role ENUM('ADMIN', 'MANAGER') NOT NULL;
