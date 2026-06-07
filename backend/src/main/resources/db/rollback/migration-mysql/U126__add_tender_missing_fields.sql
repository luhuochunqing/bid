-- Input: migration-mysql/V126__add_tender_missing_fields.sql
-- Output: rollback script for mysql environments

ALTER TABLE tenders
    DROP COLUMN contact_tel,
    DROP COLUMN contact_mail,
    DROP COLUMN contact_name2,
    DROP COLUMN contact_phone2,
    DROP COLUMN contact_tel2,
    DROP COLUMN contact_mail2,
    DROP COLUMN project_type,
    DROP COLUMN project_manager_id,
    DROP COLUMN project_manager_name,
    DROP COLUMN bidding_person_id,
    DROP COLUMN bidding_person_name,
    DROP COLUMN department,
    DROP COLUMN distributor_id,
    DROP COLUMN distributor_name,
    DROP COLUMN creator_id,
    DROP COLUMN creator_name;
