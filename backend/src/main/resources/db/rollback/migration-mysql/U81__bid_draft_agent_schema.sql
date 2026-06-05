-- Input: migration-mysql/V81__bid_draft_agent_schema.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

DROP INDEX idx_bid_agent_artifacts_status ON bid_agent_artifacts;
DROP INDEX idx_bid_agent_artifacts_type ON bid_agent_artifacts;
DROP INDEX idx_bid_agent_artifacts_run ON bid_agent_artifacts;
DROP TABLE IF EXISTS bid_agent_artifacts;
DROP INDEX idx_bid_agent_runs_status ON bid_agent_runs;
DROP INDEX idx_bid_agent_runs_tender ON bid_agent_runs;
DROP INDEX idx_bid_agent_runs_project ON bid_agent_runs;
DROP TABLE IF EXISTS bid_agent_runs;
