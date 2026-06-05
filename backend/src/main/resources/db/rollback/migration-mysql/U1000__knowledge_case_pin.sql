-- Input: V1000__knowledge_case_pin.sql
-- Rollback for V1000__knowledge_case_pin.sql

-- No-op rollback: dropping the is_pinned column removes pin state without further risk
ALTER TABLE knowledge_case DROP COLUMN IF EXISTS is_pinned;
