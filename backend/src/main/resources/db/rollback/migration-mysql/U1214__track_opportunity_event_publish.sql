-- Input: migration-mysql/V1214__track_opportunity_event_publish.sql
-- Output: rollback script for mysql environments; event retry state is discarded.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- PR: 待创建（商机预测事件失败补偿）

-- U1214: 回滚事件库投递状态。待重试信息会丢失，提醒去重记录保留。
ALTER TABLE opportunity_prediction_reminder_log
    DROP COLUMN IF EXISTS event_publish_claimed_at,
    DROP COLUMN IF EXISTS event_publish_claim_token,
    DROP COLUMN IF EXISTS event_published_at;
