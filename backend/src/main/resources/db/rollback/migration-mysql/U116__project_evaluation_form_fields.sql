-- Rollback: V116__project_evaluation_form_fields.sql
-- Input: migration-mysql/V116__project_evaluation_form_fields.sql
-- Output: rollback for mysql environments; review data-loss comments before production use.

-- 警告：数据丢失风险
-- 此回滚会删除项目评估表单的7个新增字段及所有关联数据

ALTER TABLE project_evaluation
    DROP COLUMN IF EXISTS background,
    DROP COLUMN IF EXISTS competitors,
    DROP COLUMN IF EXISTS contract_period,
    DROP COLUMN IF EXISTS shortlisted_bidders,
    DROP COLUMN IF EXISTS platform_fee,
    DROP COLUMN IF EXISTS previous_bid,
    DROP COLUMN IF EXISTS recommendation;
