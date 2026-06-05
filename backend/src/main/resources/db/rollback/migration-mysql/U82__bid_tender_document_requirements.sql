-- Input: migration-mysql/V82__bid_tender_document_requirements.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

DROP INDEX idx_bid_requirement_items_document ON bid_requirement_items;
DROP INDEX idx_bid_requirement_items_tender ON bid_requirement_items;
DROP INDEX idx_bid_requirement_items_project ON bid_requirement_items;
DROP TABLE IF EXISTS bid_requirement_items;
DROP INDEX idx_bid_tender_doc_snap_document ON bid_tender_document_snapshots;
DROP INDEX idx_bid_tender_doc_snap_tender ON bid_tender_document_snapshots;
DROP INDEX idx_bid_tender_doc_snap_project ON bid_tender_document_snapshots;
DROP TABLE IF EXISTS bid_tender_document_snapshots;
