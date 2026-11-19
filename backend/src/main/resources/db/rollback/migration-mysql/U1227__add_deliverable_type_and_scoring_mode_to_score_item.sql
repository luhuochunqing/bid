-- Input: migration-mysql/V1227__add_deliverable_type_and_scoring_mode_to_score_item.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1227: 回滚 add_deliverable_type_and_scoring_mode_to_score_item
ALTER TABLE score_item
    DROP COLUMN deliverable_type,
    DROP COLUMN scoring_mode;
