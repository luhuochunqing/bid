-- Input: migration-mysql/V83__bid_agent_requirement_snapshot_indexes.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

DROP INDEX idx_bid_requirement_items_project_document_created ON bid_requirement_items;
DROP INDEX idx_bid_tender_doc_snap_project_created ON bid_tender_document_snapshots;
