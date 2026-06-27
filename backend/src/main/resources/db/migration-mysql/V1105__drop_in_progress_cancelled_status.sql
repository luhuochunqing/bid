-- CO-361: 任务三态模型收口，废弃 IN_PROGRESS 和 CANCELLED
-- 业务侧只采用三态：TODO → REVIEW → COMPLETED（审核驳回回 TODO）。
-- V1101 已将存量 IN_PROGRESS 归一为 TODO；本迁移补做 CANCELLED→TODO 归一，
-- 并收口 ENUM 列定义，从 schema 层彻底移除两个废弃状态值。

-- 1. 存量 CANCELLED 任务归一为 TODO
UPDATE tasks SET status = 'TODO' WHERE status = 'CANCELLED';

-- 2. 收口 ENUM：只保留三个合法状态值
ALTER TABLE tasks MODIFY COLUMN status enum ('TODO','REVIEW','COMPLETED') NOT NULL;

-- 3. 状态字典：禁用 IN_PROGRESS 行（V101 种子数据）
--    保留行记录以维护主数据完整性，仅置为 enabled=FALSE 使其不再出现在前端状态选择器。
UPDATE task_status_dict SET enabled = FALSE WHERE code = 'IN_PROGRESS';
