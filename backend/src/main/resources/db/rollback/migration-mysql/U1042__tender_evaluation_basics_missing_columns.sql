-- Input: V1042__tender_evaluation_basics_missing_columns.sql
-- Rollback V1042: 删除 tender_evaluation_basics 中补齐的列
ALTER TABLE tender_evaluation_basics
    DROP COLUMN IF EXISTS planned_shortlisted_count,
    DROP COLUMN IF EXISTS mro_office_flow_amount,
    DROP COLUMN IF EXISTS contingency_plan;
