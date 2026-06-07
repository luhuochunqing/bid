-- Input: migration-mysql/V105__tender_source_document_metadata.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

ALTER TABLE tenders DROP COLUMN source_document_file_url;
ALTER TABLE tenders DROP COLUMN source_document_file_type;
ALTER TABLE tenders DROP COLUMN source_document_name;
