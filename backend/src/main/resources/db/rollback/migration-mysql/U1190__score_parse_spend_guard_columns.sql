-- U1189: 回滚花费守卫列
ALTER TABLE score_result DROP COLUMN reuse_kind;
ALTER TABLE score_parse_task
    DROP COLUMN chapter_hashes,
    DROP COLUMN item_set_hash,
    DROP COLUMN bid_content_hash,
    DROP COLUMN trigger_source;
