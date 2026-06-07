-- Input: migration-mysql/V75__tender_search_dimensions.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

DROP INDEX idx_tender_status_region_industry ON tenders;
DROP INDEX idx_tender_purchaser_hash ON tenders;
DROP INDEX idx_tender_industry ON tenders;
DROP INDEX idx_tender_region ON tenders;
ALTER TABLE tenders DROP COLUMN tags;
ALTER TABLE tenders DROP COLUMN description;
ALTER TABLE tenders DROP COLUMN contact_phone;
ALTER TABLE tenders DROP COLUMN contact_name;
ALTER TABLE tenders DROP COLUMN publish_date;
ALTER TABLE tenders DROP COLUMN purchaser_hash;
ALTER TABLE tenders DROP COLUMN purchaser_name;
ALTER TABLE tenders DROP COLUMN industry;
ALTER TABLE tenders DROP COLUMN region;
