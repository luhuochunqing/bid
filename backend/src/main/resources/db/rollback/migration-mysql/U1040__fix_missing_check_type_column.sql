-- Input: V1040__fix_missing_check_type_column.sql
-- Description: Drop check_type column from compliance_check_results

ALTER TABLE compliance_check_results DROP COLUMN IF EXISTS check_type;
