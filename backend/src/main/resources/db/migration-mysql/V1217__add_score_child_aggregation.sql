-- Preserve the validated parent-to-child scoring relationship in each review fact snapshot.
-- Existing rows intentionally remain NULL and resolve to legacy SUM semantics in the domain layer.
ALTER TABLE score_parse_fact
    ADD COLUMN child_aggregation VARCHAR(32) NULL COMMENT 'Direct child scoring relationship';
