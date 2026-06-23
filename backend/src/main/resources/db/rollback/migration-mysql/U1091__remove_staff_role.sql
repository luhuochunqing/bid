-- Input: V1091__remove_staff_role.sql
-- Data rollback required: 回滚 V1091__remove_staff_role.sql。
--  - 恢复 users.role 枚举包含 STAFF。
--  - 恢复 roles 表中的 staff 角色定义。
--  - 从备份表恢复被迁移用户的原始 role_id、role、enabled。

-- 1. 恢复 role 枚举以容纳 STAFF。
ALTER TABLE users MODIFY COLUMN role ENUM('ADMIN', 'MANAGER', 'STAFF') NOT NULL;

-- 2. 恢复 staff 角色定义。
INSERT INTO roles (code, name, description, is_system, enabled, data_scope, menu_permissions, created_at, updated_at)
VALUES ('staff',
        '员工',
        '业务人员，可查看工作台、标讯、项目、知识库与资源',
        true,
        true,
        'self',
        'dashboard,bidding,project,knowledge,resource',
        current_timestamp(6),
        current_timestamp(6));

-- 3. 从备份恢复被迁移用户的原始状态。
UPDATE users u
       JOIN V1091_staff_users_backup b ON u.id = b.user_id
SET u.role_id = b.original_role_id,
    u.role    = b.original_role,
    u.enabled = b.original_enabled;

-- 4. 清理备份表（回滚后备份不再 needed）。
DROP TABLE IF EXISTS V1091_staff_users_backup;
