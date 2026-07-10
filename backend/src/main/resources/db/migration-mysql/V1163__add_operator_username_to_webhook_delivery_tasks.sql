-- CO-152 补齐：CRM 回调链路改为按用户身份调用
-- webhook_delivery_tasks 新增 operator_username 列，记录触发操作的用户名
-- 异步回调时用它取该用户的 OSS token 调 generateToken，不再用全局 03595

ALTER TABLE webhook_delivery_tasks
    ADD COLUMN operator_username VARCHAR(128) NULL COMMENT 'CO-152: 操作者 username，webhook 回调时用它取该用户的 OSS token 调 generateToken';
