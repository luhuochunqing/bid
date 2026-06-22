-- Input: existing roles row/menu_permissions for bid_other_dept
-- Output: bid_other_dept can see the top-level task board menu
-- Pos: Flyway data migration for 西域数智化投标管理平台.
-- 维护声明: append-only role permission update; do not overwrite custom permissions.

INSERT INTO roles (code, name, description, is_system, enabled, data_scope, menu_permissions, created_at, updated_at)
VALUES (
    'bid_other_dept',
    '跨部门协同人员',
    '项目任务处理',
    true,
    true,
    'self',
    'task-board,task.view.own,task.handle.own,dashboard:view_welcome_banner,dashboard:view_technical_task,dashboard:view_activity_list,dashboard:view_priority_todos',
    NOW(),
    NOW()
)
ON DUPLICATE KEY UPDATE
    menu_permissions = CASE
        WHEN FIND_IN_SET('task-board', COALESCE(menu_permissions, '')) > 0 THEN menu_permissions
        WHEN menu_permissions IS NULL OR TRIM(menu_permissions) = '' THEN 'task-board'
        ELSE CONCAT(menu_permissions, ',task-board')
    END,
    updated_at = CASE
        WHEN FIND_IN_SET('task-board', COALESCE(menu_permissions, '')) > 0 THEN updated_at
        ELSE NOW()
    END;
