-- U1204: 回滚 V1204 —— 删除 score_parse_task.run_summary
ALTER TABLE score_parse_task
    DROP COLUMN run_summary;
