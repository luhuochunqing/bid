-- Input: migration-mysql/V1195__add_original_total_weight.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1195: 回滚 add_original_total_weight（删除列，original_total_weight 无外键依赖，可直接 DROP）
ALTER TABLE score_parse_task DROP COLUMN original_total_weight;