-- V1112: 清理历史"【待立项】"占位任务（根治 CO-400 复发）
-- 根因: TenderEvaluationService.proceedToBid 历史上自动创建"【待立项】"占位任务，
--       前端主动过滤不展示，但会卡住 AllTasksCompletedPolicy 闸门，导致
--       submit-bid 报"仍有 N 个任务未完成，无法提交投标"。
-- 历史: V1098 曾尝试通过 CANCELLED 归一，但 V1105（CO-361 三态收口）又把它
--       改回 TODO，导致问题复发。
-- 操作: 直接删除（数据全为历史测试数据，前端本就不展示，删除零业务影响）。
-- Idempotency: DELETE 带 WHERE 条件，安全重跑为 no-op。
-- Backout: 无回滚（测试数据可重新创建）；如需恢复，从备份表或 binlog 恢复。
-- PR: 合入 main 后由 DevOps 执行。

-- Pre-flight: 统计待清理条数。
SELECT COUNT(*) AS pending_initiation_todo_before
  FROM tasks
 WHERE status = 'TODO'
   AND title LIKE '【待立项】%';

-- 执行清理：删除前端不可见的历史占位任务。
DELETE
  FROM tasks
 WHERE status = 'TODO'
   AND title LIKE '【待立项】%';

-- Post-flight: 确认无 TODO 残留。
SELECT COUNT(*) AS remaining_pending_initiation_todo
  FROM tasks
 WHERE status = 'TODO'
   AND title LIKE '【待立项】%';
