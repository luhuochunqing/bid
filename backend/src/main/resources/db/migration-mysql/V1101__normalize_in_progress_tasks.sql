-- CO-361: 任务三态模型收口
-- 将所有 IN_PROGRESS 状态的任务归一为 TODO
-- IN_PROGRESS 在业务层已废弃（三态模型：TODO → REVIEW → COMPLETED）
-- 此迁移保证历史 IN_PROGRESS 任务在看板中可见

UPDATE tasks SET status = 'TODO' WHERE status = 'IN_PROGRESS';
