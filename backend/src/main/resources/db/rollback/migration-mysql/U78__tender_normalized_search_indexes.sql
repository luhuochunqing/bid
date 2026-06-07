-- Input: migration-mysql/V78__tender_normalized_search_indexes.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

DROP INDEX idx_tender_status_region_industry_normalized ON tenders;
DROP INDEX idx_tender_purchaser_hash_normalized ON tenders;
DROP INDEX idx_tender_industry_normalized ON tenders;
DROP INDEX idx_tender_region_normalized ON tenders;
DROP INDEX idx_tender_source_normalized ON tenders;
-- Data rollback required for UPDATE tenders; original values are not stored in migration history.
ALTER TABLE tenders DROP COLUMN search_text_normalized;
ALTER TABLE tenders DROP COLUMN purchaser_name_normalized;
ALTER TABLE tenders DROP COLUMN purchaser_hash_normalized;
ALTER TABLE tenders DROP COLUMN industry_normalized;
ALTER TABLE tenders DROP COLUMN region_normalized;
ALTER TABLE tenders DROP COLUMN source_normalized;
