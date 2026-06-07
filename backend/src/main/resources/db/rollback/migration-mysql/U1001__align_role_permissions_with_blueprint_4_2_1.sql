-- Input: V1001__align_role_permissions_with_blueprint_4_2_1.sql
-- Rollback for V1001__align_role_permissions_with_blueprint_4_2_1.sql

-- bid_admin: restore data_scope to dept, remove bidding.manage/create/delete/sync
UPDATE roles
SET data_scope = 'dept',
    menu_permissions = TRIM(BOTH ',' FROM REPLACE(
        REPLACE(
            REPLACE(
                REPLACE(CONCAT(',', menu_permissions, ','),
                    ',bidding.manage,', ','),
                ',bidding.create,', ','),
            ',bidding.delete,', ','),
        ',bidding.sync,', ',')
    ),
    updated_at = NOW()
WHERE code = 'bid_admin';

-- bid_lead: restore data_scope to self, remove bidding.manage/create/delete
UPDATE roles
SET data_scope = 'self',
    menu_permissions = TRIM(BOTH ',' FROM REPLACE(
        REPLACE(
            REPLACE(CONCAT(',', menu_permissions, ','),
                ',bidding.manage,', ','),
            ',bidding.create,', ','),
        ',bidding.delete,', ',')
    ),
    updated_at = NOW()
WHERE code = 'bid_lead';

-- sales: remove bidding.create
UPDATE roles
SET menu_permissions = TRIM(BOTH ',' FROM REPLACE(
    CONCAT(',', menu_permissions, ','),
    ',bidding.create,', ','
)),
    updated_at = NOW()
WHERE code = 'sales';

-- bid_specialist: remove bidding.create
UPDATE roles
SET menu_permissions = TRIM(BOTH ',' FROM REPLACE(
    CONCAT(',', menu_permissions, ','),
    ',bidding.create,', ','
)),
    updated_at = NOW()
WHERE code = 'bid_specialist';
