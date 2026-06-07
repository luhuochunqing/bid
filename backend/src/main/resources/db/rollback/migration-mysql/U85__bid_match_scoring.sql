-- Input: migration-mysql/V85__bid_match_scoring.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

DROP INDEX idx_bid_match_eval_time ON bid_match_score_evaluations;
DROP INDEX idx_bid_match_eval_version ON bid_match_score_evaluations;
DROP INDEX idx_bid_match_eval_tender ON bid_match_score_evaluations;
DROP TABLE IF EXISTS bid_match_score_evaluations;
DROP INDEX uk_bid_match_version_model_no ON bid_match_model_versions;
DROP INDEX idx_bid_match_version_active ON bid_match_model_versions;
DROP INDEX idx_bid_match_version_model ON bid_match_model_versions;
DROP TABLE IF EXISTS bid_match_model_versions;
DROP INDEX idx_bid_match_model_status ON bid_match_scoring_models;
DROP TABLE IF EXISTS bid_match_scoring_models;
