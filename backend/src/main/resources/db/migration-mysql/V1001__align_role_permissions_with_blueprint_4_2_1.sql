-- V1001__align_role_permissions_with_blueprint_4_2_1.sql
-- 产品蓝图 §4.2.1 角色与权限：对齐 dataScope 和菜单权限
--
-- 变更内容：
-- 1. bid_admin: data_scope dept→all，新增 bidding.manage/create/delete/sync
-- 2. bid_lead: data_scope self→all，新增 bidding.manage/create/delete
-- 3. sales: 新增 bidding.create
-- 4. bid_specialist: 新增 bidding.create

-- 投标部门管理员：全量数据范围 + 标讯管理/创建/删除/同步权限
UPDATE roles
SET data_scope = 'all',
    menu_permissions = CONCAT(
        TRIM(',' FROM CONCAT_WS(',',
            menu_permissions,
            'bidding.manage',
            'bidding.create',
            'bidding.delete',
            'bidding.sync'
        ))
    ),
    updated_at = NOW()
WHERE code = 'bid_admin';

-- 投标负责人：全量数据范围 + 标讯管理/创建/删除权限
UPDATE roles
SET data_scope = 'all',
    menu_permissions = CONCAT(
        TRIM(',' FROM CONCAT_WS(',',
            menu_permissions,
            'bidding.manage',
            'bidding.create',
            'bidding.delete'
        ))
    ),
    updated_at = NOW()
WHERE code = 'bid_lead';

-- 项目负责人：新增 bidding.create
UPDATE roles
SET menu_permissions = CONCAT(
    TRIM(',' FROM CONCAT_WS(',',
        menu_permissions,
        'bidding.create'
    ))
),
    updated_at = NOW()
WHERE code = 'sales';

-- 投标专员：新增 bidding.create
UPDATE roles
SET menu_permissions = CONCAT(
    TRIM(',' FROM CONCAT_WS(',',
        menu_permissions,
        'bidding.create'
    ))
),
    updated_at = NOW()
WHERE code = 'bid_specialist';
