-- V1010: Add brand-auth permissions to existing role profiles
-- Brand Authorization §4.6a — 原厂授权 manufacturer authorization permissions

-- bidAdmin (bid部门管理员): view + create + edit + revoke
UPDATE roles
SET menu_permissions = CONCAT(menu_permissions, ',brand-auth.view,brand-auth.create,brand-auth.edit,brand-auth.revoke,knowledge-brand-auth')
WHERE code = 'bidAdmin' AND menu_permissions NOT LIKE '%brand-auth%';

-- bid-TeamLeader (bid组长): view + create + edit + revoke
UPDATE roles
SET menu_permissions = CONCAT(menu_permissions, ',brand-auth.view,brand-auth.create,brand-auth.edit,brand-auth.revoke,knowledge-brand-auth')
WHERE code = 'bid-TeamLeader' AND menu_permissions NOT LIKE '%brand-auth%';

-- bid-Team (bid专员): view + create + edit (no revoke)
UPDATE roles
SET menu_permissions = CONCAT(menu_permissions, ',brand-auth.view,brand-auth.create,brand-auth.edit,knowledge-brand-auth')
WHERE code = 'bid-Team' AND menu_permissions NOT LIKE '%brand-auth%';
