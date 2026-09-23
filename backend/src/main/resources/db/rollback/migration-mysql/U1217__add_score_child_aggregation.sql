-- Input: backend/src/main/resources/db/migration-mysql/V1217__add_score_child_aggregation.sql
-- Remove only the optional relationship contract; no historical score facts are rewritten.
ALTER TABLE score_parse_fact
    DROP COLUMN child_aggregation;
