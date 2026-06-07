-- V1010: Add brand-auth permissions to existing role profiles
-- Brand Authorization §4.6a — 原厂授权 manufacturer authorization permissions

-- bid_admin (bid部门管理员): view + create + edit + revoke
UPDATE roles
SET menu_permissions = CONCAT(menu_permissions, ',brand-auth.view,brand-auth.create,brand-auth.edit,brand-auth.revoke,knowledge-brand-auth')
WHERE code = 'bid_admin' AND menu_permissions NOT LIKE '%brand-auth%';

-- bid_lead (bid组长): view + create + edit + revoke
UPDATE roles
SET menu_permissions = CONCAT(menu_permissions, ',brand-auth.view,brand-auth.create,brand-auth.edit,brand-auth.revoke,knowledge-brand-auth')
WHERE code = 'bid_lead' AND menu_permissions NOT LIKE '%brand-auth%';

-- bid_specialist (bid专员): view + create + edit (no revoke)
UPDATE roles
SET menu_permissions = CONCAT(menu_permissions, ',brand-auth.view,brand-auth.create,brand-auth.edit,knowledge-brand-auth')
WHERE code = 'bid_specialist' AND menu_permissions NOT LIKE '%brand-auth%';
