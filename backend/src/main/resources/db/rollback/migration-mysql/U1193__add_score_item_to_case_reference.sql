-- Undo V1193: drop added columns from case_reference_records
ALTER TABLE case_reference_records
    DROP INDEX IF EXISTS idx_case_ref_score_item,
    DROP INDEX IF EXISTS idx_case_ref_project,
    DROP COLUMN IF EXISTS reuse_mode,
    DROP COLUMN IF EXISTS score_item_id,
    DROP COLUMN IF EXISTS project_id;
