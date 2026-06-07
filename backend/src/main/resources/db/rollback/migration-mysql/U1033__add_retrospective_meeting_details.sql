-- Input: migration-mysql/V1033__add_retrospective_meeting_details.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- U1033: 回滚项目复盘会议详情字段 + knowledge_case AI 推荐增强字段（合并自 V1034）
ALTER TABLE project_retrospective DROP COLUMN IF EXISTS meeting_time;
ALTER TABLE project_retrospective DROP COLUMN IF EXISTS meeting_type;
ALTER TABLE project_retrospective DROP COLUMN IF EXISTS participants;
ALTER TABLE project_retrospective DROP COLUMN IF EXISTS attachment_id;
ALTER TABLE knowledge_case DROP COLUMN IF EXISTS bid_result;
ALTER TABLE knowledge_case DROP COLUMN IF EXISTS scoring_category;
ALTER TABLE knowledge_case DROP INDEX IF EXISTS idx_knowledge_case_category;
ALTER TABLE knowledge_case DROP INDEX IF EXISTS idx_knowledge_case_bid_result;
