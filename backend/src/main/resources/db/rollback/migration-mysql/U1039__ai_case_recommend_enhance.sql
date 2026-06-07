-- Input: V1039__ai_case_recommend_enhance.sql
-- Rollback for V1039__ai_case_recommend_enhance.sql
ALTER TABLE knowledge_case
    DROP COLUMN bid_result,
    DROP COLUMN scoring_category;
