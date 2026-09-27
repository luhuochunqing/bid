-- Input: migration-mysql/V1219__score_extraction_versions.sql
-- Output: guarded rollback; export snapshots before destructive DDL.
-- Pos: Flyway MySQL extraction version rollback.
-- U1219 rollback for spec 073; PR pending. Export new snapshots before rollback.
-- Refuse unknown->zero coercion BEFORE destructive DDL; this guard works in MySQL 8.0.
DROP TEMPORARY TABLE IF EXISTS score_extraction_rollback_guard;
CREATE TEMPORARY TABLE score_extraction_rollback_guard (
    unknown_count BIGINT NOT NULL CHECK (unknown_count = 0)
);
INSERT INTO score_extraction_rollback_guard SELECT COUNT(*) FROM score_parse_fact WHERE weight IS NULL;
DROP TEMPORARY TABLE score_extraction_rollback_guard;
ALTER TABLE score_parse_fact MODIFY COLUMN weight DECIMAL(6,2) NOT NULL COMMENT '权重分值';
ALTER TABLE score_item DROP COLUMN source_fact_id, DROP COLUMN active_version_id, DROP COLUMN rule_fingerprint;
ALTER TABLE score_parse_fact DROP COLUMN item_payload, DROP COLUMN payload_version;
DROP TABLE IF EXISTS score_active_version;
DROP TABLE IF EXISTS score_extraction_snapshot;
