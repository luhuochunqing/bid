-- V1165: 新增 bid-SystemAdmin 角色（OSS 端"投标系统管理员"角色码）
--
-- 背景：
--   之前 bid-SystemAdmin 通过 JobRoleLookupResolver.mapOssRoleCodeToInternal() 映射为 admin，
--   导致 OSS 端配置的菜单权限失效（admin 有 "all" 权限）。
--   现将 bid-SystemAdmin 作为独立的第 8 个角色码，权限配置与 /bidAdmin 一致，
--   但使用细粒度 menuPermissions，OSS 端配置的菜单权限对其生效。
--
-- 安全策略：
--   - 使用 INSERT IGNORE 避免重复插入
--   - menu_permissions 与 /bidAdmin 完全一致
--   - data_scope = 'all'（全局数据权限）
--   - 不包含 'all' 权限（避免覆盖 OSS 菜单配置）

INSERT IGNORE INTO roles (code, name, description, is_system, enabled, data_scope, menu_permissions, created_at, updated_at)
VALUES (
    'bid-SystemAdmin',
    '投标系统管理员',
    'OSS 端投标系统管理员，权限等同投标管理员',
    true,
    true,
    'all',
    'dashboard,operation-logs,bidding,project,knowledge,resource,analytics,settings,settings-alerts,task.review,retrospective.submit,retrospective.review,closure.review,lead.assign,bidding.manage,bidding.create,bidding.delete,bidding.sync,brand-auth.view,brand-auth.create,brand-auth.edit,brand-auth.revoke,knowledge-brand-auth,tender.view,personnel.view,personnel.manage,performance.manage,qualification.manage,qualification.view,dashboard:view_welcome_banner,dashboard:view_metric_cards,dashboard:view_calendar,dashboard:view_tender_list,dashboard:view_project_list,dashboard:view_team_task,dashboard:view_global_projects,dashboard:view_active_projects,dashboard:view_team_performance,dashboard:view_approval_list,dashboard:view_process_timeline,dashboard:view_activity_list,dashboard:view_priority_todos,warehouse.manage',
    current_timestamp(6),
    current_timestamp(6)
);
