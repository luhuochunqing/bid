-- Remove only the optional relationship contract; no historical score facts are rewritten.
ALTER TABLE score_parse_fact
    DROP COLUMN child_aggregation;
