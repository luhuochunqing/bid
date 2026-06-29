-- Input: V1112__cleanup_legacy_pending_initiation_tasks.sql
-- U1112: V1112 回滚脚本（NO-OP）
-- 本次清理的数据为历史测试占位任务，前端不可见，删除后无法精确回滚。
-- 如需恢复，可从数据库备份或 binlog 中还原。
-- 如有需要，可手动重建占位任务（参考 CO-349 修复前的 TenderBidTaskFactory 逻辑）。
-- No-op rollback: 历史测试数据清理无需精确回滚
SELECT 'NO-OP: V1112 清理的历史测试数据无精确回滚需求，如需恢复请从备份还原' AS rollback_note;
