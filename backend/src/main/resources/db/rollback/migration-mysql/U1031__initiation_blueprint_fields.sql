-- Input: V1031__initiation_blueprint_fields.sql
-- Rollback for V1031__initiation_blueprint_fields.sql

ALTER TABLE project_initiation_details
  DROP COLUMN annual_ecommerce_amount,
  DROP COLUMN need_deposit,
  DROP COLUMN tender_adverse_items,
  DROP COLUMN risk_assessment,
  DROP COLUMN risk_mitigation_plan,
  DROP COLUMN pm_understands_process,
  DROP COLUMN support_needed,
  DROP COLUMN project_plan_gap,
  DROP COLUMN headquarters_location,
  DROP COLUMN ai_risk_assessment_notes;
