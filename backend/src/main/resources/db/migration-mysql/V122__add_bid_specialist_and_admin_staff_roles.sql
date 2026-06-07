-- 新增产品蓝图角色：投标专员、行政人员
-- 与 RoleProfileCatalog.seedDefinitions() 保持一致

insert into roles (code, name, description, is_system, enabled, data_scope, menu_permissions, created_at, updated_at)
values
    ('bid_specialist', '投标专员', '投标辅助、标书审核与任务处理', true, true, 'self', 'dashboard,bidding,project,knowledge,resource,task.view.own,task.handle.own,evaluation.update', current_timestamp(6), current_timestamp(6)),
    ('admin_staff', '行政人员', '资质证书管理与行政事务', true, true, 'self', 'dashboard,knowledge,resource,certificate.manage,qualification.view', current_timestamp(6), current_timestamp(6))
on duplicate key update
    name = values(name),
    description = values(description),
    is_system = values(is_system),
    enabled = values(enabled),
    data_scope = values(data_scope),
    menu_permissions = values(menu_permissions),
    updated_at = current_timestamp(6);
