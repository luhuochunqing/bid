-- Input: migration-mysql/V1114__add_bid_document_review_table.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1114: 回滚 add_bid_document_review_table
-- 回滚会丢失标书审核记录数据，生产回滚前请先备份。
DROP TABLE IF EXISTS bid_document_review;
