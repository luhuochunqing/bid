-- 回滚 V1163：移除 webhook_delivery_tasks.operator_username 列
-- CO-152 补齐：CRM 回调链路改造
-- Input: V1163__add_operator_username_to_webhook_delivery_tasks.sql

ALTER TABLE webhook_delivery_tasks
    DROP COLUMN operator_username;
