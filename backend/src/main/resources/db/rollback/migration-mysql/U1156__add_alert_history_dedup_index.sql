-- Input: migration-mysql/V1156__add_alert_history_dedup_index.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1156: 回滚 add_alert_history_dedup_index
-- 删除 V1156 添加的复合索引，恢复到仅有主键索引的状态。
-- 注意：回滚后去重查询将退化为全表扫描，仅在告警历史数据量较小时可接受。

DROP INDEX idx_alert_history_dedup ON alert_history;
