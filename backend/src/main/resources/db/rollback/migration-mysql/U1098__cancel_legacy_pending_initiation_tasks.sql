-- Input: V1098__cancel_legacy_pending_initiation_tasks.sql
-- U1098: 回滚 V1098 (CO-349 历史占位任务清理)
-- Backout strategy: 仅恢复"看起来被 V1098 改过"的行——即 title 以"【待立项】"开头、当前
--   status='CANCELLED' 的任务，把它们改回 'TODO'。
-- 注意: 若 V1098 之后有其他流程合法地把某条"【待立项】"任务改为 CANCELLED，回滚会一并还原。
--   此类任务为纯占位 (前端不展示、无看板/通知依赖)，还原为 TODO 影响可控。
--   不可完全精确回滚 (V1098 未记录原始快照)；如需精确还原，请从 V1098 执行前备份恢复。
-- Idempotency: WHERE status='CANCELLED' 自排除。
-- Forward: db/migration-mysql/V1098__cancel_legacy_pending_initiation_tasks.sql

-- Pre-flight
SELECT COUNT(*) AS pending_initiation_cancelled_before
  FROM tasks
 WHERE status = 'CANCELLED'
   AND title LIKE '【待立项】%';

-- 回滚：CANCELLED -> TODO。可安全重跑 (WHERE 自排除)。
UPDATE tasks
   SET status = 'TODO',
       updated_at = NOW()
 WHERE status = 'CANCELLED'
   AND title LIKE '【待立项】%';

-- Post-flight
SELECT COUNT(*) AS pending_initiation_todo_restored_after
  FROM tasks
 WHERE status = 'TODO'
   AND title LIKE '【待立项】%';
