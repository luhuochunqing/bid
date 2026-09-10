-- V1214: 区分商机预测“已提醒”和“事件库已受理”，支持事件失败后只重推事件。
-- 历史提醒视为已完成事件投递，避免上线后重放全部历史项目。

ALTER TABLE opportunity_prediction_reminder_log
    ADD COLUMN event_published_at DATETIME NULL AFTER notified_at;

ALTER TABLE opportunity_prediction_reminder_log
    ADD COLUMN event_publish_claim_token VARCHAR(36) NULL AFTER event_published_at;

ALTER TABLE opportunity_prediction_reminder_log
    ADD COLUMN event_publish_claimed_at DATETIME NULL AFTER event_publish_claim_token;

UPDATE opportunity_prediction_reminder_log
SET event_published_at = notified_at
WHERE event_published_at IS NULL;
