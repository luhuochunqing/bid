-- Input: V1028__tender_crm_project_link.sql
-- Rollback for V1028__tender_crm_project_link.sql
ALTER TABLE tenders
    DROP COLUMN crm_opportunity_name,
    DROP COLUMN project_id;
