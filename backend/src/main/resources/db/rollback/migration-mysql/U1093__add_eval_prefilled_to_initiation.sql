-- Input: V1093__add_eval_prefilled_to_initiation.sql
-- Rollback: 移除 eval_prefilled 列。

ALTER TABLE project_initiation_details DROP COLUMN eval_prefilled;
