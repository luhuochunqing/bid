-- V1156: add_alert_history_dedup_index
-- 说明: 为 alert_history 表添加复合索引，优化告警去重查询性能
--
-- 背景：AlertHistoryService.createAlertHistoryIfAbsent() 执行去重查询：
--   findFirstByRuleIdAndRelatedIdAndResolvedFalseOrderByCreatedAtDesc(ruleId, relatedId)
-- 即 WHERE rule_id = ? AND related_id = ? AND resolved = false ORDER BY created_at DESC
--
-- 改造前：alert_history 表仅有主键索引，去重查询全表扫描。
-- 随着告警历史增长，每次扫描器调用都会触发去重查询，性能瓶颈明显。
--
-- 改造后：添加复合索引 (rule_id, related_id, resolved, created_at DESC)
-- - 前三列覆盖 WHERE 条件，等值匹配
-- - created_at DESC 作为索引尾列，支持 ORDER BY 索引扫描（避免 filesort）
-- - 查询从全表扫描退化为索引范围扫描

CREATE INDEX idx_alert_history_dedup ON alert_history (rule_id, related_id, resolved, created_at DESC);
