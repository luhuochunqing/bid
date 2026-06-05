-- V112: align project_initiation_details with ProjectInitiationDetails entity
-- Adds owner_unit / project_type / owner_user_id missing in V108

ALTER TABLE project_initiation_details
    ADD COLUMN owner_unit VARCHAR(255) NULL,
    ADD COLUMN project_type VARCHAR(64) NULL,
    ADD COLUMN owner_user_id BIGINT NULL;
