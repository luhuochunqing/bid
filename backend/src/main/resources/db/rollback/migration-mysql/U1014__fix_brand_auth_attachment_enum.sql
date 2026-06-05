-- Input: V1014__fix_brand_auth_attachment_enum.sql
-- Rollback for V1014__fix_brand_auth_attachment_enum.sql

ALTER TABLE brand_auth_attachment
    MODIFY COLUMN attachment_type VARCHAR(20) NOT NULL;
