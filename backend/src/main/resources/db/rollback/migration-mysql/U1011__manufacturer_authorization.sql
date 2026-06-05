-- Input: V1011__manufacturer_authorization.sql
-- Rollback for V1011__manufacturer_authorization.sql

DROP TABLE IF EXISTS brand_auth_attachment;
DROP TABLE IF EXISTS manufacturer_authorization;
ALTER TABLE brand_authorization_deprecated RENAME TO brand_authorization;
