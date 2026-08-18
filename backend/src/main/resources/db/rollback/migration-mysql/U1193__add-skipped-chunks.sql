-- Input: migration-mysql/V1193__add-skipped-chunks.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1193: 回滚 add-skipped-chunks（丢弃跳过 chunk 计数，旧任务可见性字段回到缺失态）
ALTER TABLE score_parse_task DROP COLUMN skipped_chunks;
