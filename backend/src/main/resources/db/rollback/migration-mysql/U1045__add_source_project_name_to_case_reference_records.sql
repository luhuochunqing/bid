-- Input: V1045__add_source_project_name_to_case_reference_records.sql
-- Description: Drop source_project_name column from case_reference_records

ALTER TABLE case_reference_records DROP COLUMN IF EXISTS source_project_name;
