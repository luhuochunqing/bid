-- Input: V1054__add_webhook_delivery_task_tables.sql
-- Rollback for V1054__add_webhook_delivery_task_tables.sql

-- PR: N/A (local agent change)
-- 回滚 V1054__add_webhook_delivery_task_tables.sql。
-- 注意：会删除 webhook 投递任务与死信数据。

DROP TABLE IF EXISTS webhook_delivery_dlq;
DROP TABLE IF EXISTS webhook_delivery_tasks;
