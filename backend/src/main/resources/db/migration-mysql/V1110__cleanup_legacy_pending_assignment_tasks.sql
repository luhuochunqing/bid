-- V1110: 清理 CO-401 之前自动创建的遗留"【待分配】"任务
--
-- 背景：TenderCommandService.createTender() 在 CO-401 之前，当标讯自动分配
-- 失败时会调用 createPendingAssignmentTasks() 给投标管理员/组长创建
-- "【待分配】{标讯标题}"的 TODO 任务。CO-401 已删除创建逻辑（PR #1327），
-- 但数据库中已存在的遗留任务仍在，其 status=TODO 导致 BidReadinessPolicy
-- 校验"仍有 N 个任务未完成"，阻断标书审核/投标提交。
--
-- 本脚本删除所有 title LIKE '【待分配】%' 的遗留任务及其关联数据：
-- - task_deliverables（无外键约束，手动删除）
-- - task_history / task_comment（ON DELETE CASCADE，自动级联）

-- Step 1: 删除遗留任务的交付物（task_deliverables 无 FK 约束，需先手动清理）
DELETE td FROM task_deliverables td
INNER JOIN tasks t ON td.task_id = t.id
WHERE t.title LIKE '【待分配】%';

-- Step 2: 删除遗留任务（task_history / task_comment ON DELETE CASCADE 自动级联）
DELETE FROM tasks WHERE title LIKE '【待分配】%';
