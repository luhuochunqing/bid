-- Input: V1021__add_personnel_view_fields.sql
-- Rollback for V1021__add_personnel_view_fields.sql

DROP INDEX idx_personnel_gender ON personnel;
DROP INDEX idx_personnel_entry_date ON personnel;

ALTER TABLE personnel
    DROP COLUMN phone,
    DROP COLUMN entry_date,
    DROP COLUMN gender;
