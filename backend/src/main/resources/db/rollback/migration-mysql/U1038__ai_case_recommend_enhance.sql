-- Input: V1038__ai_case_recommend_enhance.sql
-- Rollback for V1038__ai_case_recommend_enhance.sql
ALTER TABLE knowledge_case
    DROP INDEX IF EXISTS idx_knowledge_case_category,
    DROP INDEX IF EXISTS idx_knowledge_case_bid_result,
    DROP COLUMN IF EXISTS bid_result,
    DROP COLUMN IF EXISTS scoring_category;
