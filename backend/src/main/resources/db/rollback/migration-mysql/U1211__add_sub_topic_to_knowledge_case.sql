-- Input: migration-mysql/V1211__add_sub_topic_to_knowledge_case.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: 维护者按项目SOP；与 V1211 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest

-- U1211: 回滚 add_sub_topic_to_knowledge_case
ALTER TABLE knowledge_case
    DROP INDEX idx_knowledge_case_sub_topic,
    DROP COLUMN sub_topic,
    DROP COLUMN quality_score;
