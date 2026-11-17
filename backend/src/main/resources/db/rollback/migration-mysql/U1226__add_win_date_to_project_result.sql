-- Input: migration-mysql/V1226__add_win_date_to_project_result.sql
-- Output: drops win_date column from project_result table.
-- Pos: db/rollback/migration-mysql/
ALTER TABLE project_result
    DROP COLUMN win_date;
