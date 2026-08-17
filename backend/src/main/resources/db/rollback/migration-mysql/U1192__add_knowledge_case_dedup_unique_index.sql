-- Input: migration-mysql/V1192__add_knowledge_case_dedup_unique_index.sql
-- Rollback for V1192__add_knowledge_case_dedup_unique_index.sql
-- U1192: 回滚 knowledge_case 的 uk_source_proj_scoring_point 唯一索引

ALTER TABLE knowledge_case
DROP INDEX IF EXISTS uk_source_proj_scoring_point;
