-- Input: migration-mysql/V86__widen_bid_tender_document_snapshot_payloads.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- Manual rollback required for column alteration on bid_tender_document_snapshots.extracted_text.
