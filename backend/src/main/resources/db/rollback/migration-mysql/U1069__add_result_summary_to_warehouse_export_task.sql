-- U1069__add_result_summary_to_warehouse_export_task.sql
-- PR: (待补)
ALTER TABLE warehouse_export_task
    DROP COLUMN IF EXISTS result_summary;
