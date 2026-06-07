-- V1014: Fix brand_auth_attachment.attachment_type column type for MySQL ENUM validation
-- The V1011 migration created VARCHAR(20) but Hibernate expects ENUM on MySQL with ddl-auto=validate

ALTER TABLE brand_auth_attachment
    MODIFY COLUMN attachment_type ENUM('AUTH_DOC', 'SUPPLEMENTARY') NOT NULL;
