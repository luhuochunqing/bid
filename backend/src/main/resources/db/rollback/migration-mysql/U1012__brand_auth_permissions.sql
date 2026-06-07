-- Input: V1012__brand_auth_permissions.sql
-- Rollback for V1012__brand_auth_permissions.sql


UPDATE roles
SET menu_permissions = REPLACE(menu_permissions, ',brand-auth.view,brand-auth.create,brand-auth.edit,brand-auth.revoke,knowledge-brand-auth', '')
WHERE code IN ('bid_admin', 'bid_lead') AND menu_permissions LIKE '%brand-auth%';

UPDATE roles
SET menu_permissions = REPLACE(menu_permissions, ',brand-auth.view,brand-auth.create,brand-auth.edit,knowledge-brand-auth', '')
WHERE code = 'bid_specialist' AND menu_permissions LIKE '%brand-auth%';
