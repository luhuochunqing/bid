-- Input: migration-mysql/V125__tender_reminder_setting_unique_constraint.sql
-- Output: rollback script removing the unique constraint added in V125.
-- Pos: Flyway down migration coverage for 西域数智化投标管理平台.

ALTER TABLE tender_reminder_settings
    DROP INDEX uk_tender_reminder_tender_type;
