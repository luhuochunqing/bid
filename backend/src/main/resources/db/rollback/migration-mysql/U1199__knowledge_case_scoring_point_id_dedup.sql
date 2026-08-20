-- Input: migration-mysql/V1199__knowledge_case_scoring_point_id_dedup.sql
-- Rollback for V1199__knowledge_case_scoring_point_id_dedup.sql
-- U1199: 回滚 knowledge_case 去重键从评分项 ID 改回标题

ALTER TABLE knowledge_case
DROP INDEX uk_source_proj_scoring_point,
ADD UNIQUE INDEX uk_source_proj_scoring_point (source_project_id, scoring_point_title);

ALTER TABLE knowledge_case
DROP COLUMN scoring_point_id;
