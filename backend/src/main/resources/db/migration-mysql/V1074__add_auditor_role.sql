-- 补充缺失的角色到 roles 表，与 RoleProfileCatalog.seedDefinitions() 保持一致
-- PR: 当前分支

insert into roles (code, name, description, is_system, enabled, data_scope, menu_permissions, created_at, updated_at)
values
    ('auditor', '审计员', '审计人员，可查看全量审计日志和个人操作日志', true, true, 'all',
     'dashboard,operation-logs,audit-logs,dashboard:view_welcome_banner,dashboard:view_metric_cards',
     current_timestamp(6), current_timestamp(6)),
    ('bid_admin', '投标部门管理员', '复盘审核与结项闸门审批', true, true, 'all',
     'dashboard,operation-logs,bidding,project,knowledge,resource,analytics,settings,task.review,retrospective.submit,retrospective.review,closure.review,lead.assign,bidding.manage,bidding.create,bidding.delete,bidding.sync,brand-auth.view,brand-auth.create,brand-auth.edit,brand-auth.revoke,knowledge-brand-auth,dashboard:view_welcome_banner,dashboard:view_metric_cards,dashboard:view_calendar,dashboard:view_tender_list,dashboard:view_project_list,dashboard:view_team_task,dashboard:view_global_projects,dashboard:view_active_projects,dashboard:view_team_performance,dashboard:view_approval_list,dashboard:view_process_timeline,dashboard:view_activity_list,dashboard:view_priority_todos,warehouse.manage',
     current_timestamp(6), current_timestamp(6)),
    ('bid_lead', '投标组长', '标书编制与评标推进负责人', true, true, 'all',
     'dashboard,bidding,project,knowledge,resource,task.assign,evaluation.update,result.register,retrospective.submit,closure.request,bidding.manage,bidding.create,bidding.delete,brand-auth.view,brand-auth.create,brand-auth.edit,brand-auth.revoke,knowledge-brand-auth,dashboard:view_welcome_banner,dashboard:view_metric_cards,dashboard:view_calendar,dashboard:view_tender_list,dashboard:view_technical_task,dashboard:view_review_list,dashboard:view_project_list,dashboard:view_active_projects,dashboard:view_activity_list,dashboard:view_priority_todos,warehouse.manage',
     current_timestamp(6), current_timestamp(6)),
    ('sales', '项目负责人', '立项发起人，维护客户与开标信息', true, true, 'self',
     'dashboard,bidding,project,knowledge,project.create,project.view,deposit.return.fill,bidding.create,dashboard:view_welcome_banner,dashboard:view_metric_cards,dashboard:view_calendar,dashboard:view_tender_list,dashboard:view_project_list,dashboard:view_active_projects,dashboard:view_activity_list,dashboard:view_priority_todos',
     current_timestamp(6), current_timestamp(6)),
    ('task_executor', '任务执行人', '标书任务承接与执行', true, true, 'self',
     'dashboard,project,knowledge,task.view.own,task.handle.own,dashboard:view_welcome_banner,dashboard:view_calendar,dashboard:view_technical_task,dashboard:view_active_projects,dashboard:view_activity_list,dashboard:view_priority_todos,evaluation.update',
     current_timestamp(6), current_timestamp(6)),
    ('bid_specialist', '投标专员', '投标辅助、标书审核与任务处理', true, true, 'self',
     'dashboard,bidding,project,knowledge,resource,task.view.own,task.handle.own,evaluation.update,retrospective.submit,bidding.create,brand-auth.view,brand-auth.create,brand-auth.edit,knowledge-brand-auth,dashboard:view_welcome_banner,dashboard:view_metric_cards,dashboard:view_calendar,dashboard:view_tender_list,dashboard:view_technical_task,dashboard:view_active_projects,dashboard:view_activity_list,dashboard:view_priority_todos,warehouse.manage',
     current_timestamp(6), current_timestamp(6)),
    ('admin_staff', '行政人员', '资质证书管理与行政事务', true, true, 'self',
     'dashboard,knowledge,resource,certificate.manage,qualification.view,dashboard:view_welcome_banner,dashboard:view_calendar,dashboard:view_active_projects,dashboard:view_activity_list',
     current_timestamp(6), current_timestamp(6))
on duplicate key update
    name = values(name),
    description = values(description),
    is_system = values(is_system),
    enabled = values(enabled),
    data_scope = values(data_scope),
    menu_permissions = values(menu_permissions),
    updated_at = current_timestamp(6);
