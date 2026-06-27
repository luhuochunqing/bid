-- Input: V1105__drop_in_progress_cancelled_status.sql
-- 回滚 V1105: 恢复 tasks.status ENUM 含 IN_PROGRESS 和 CANCELLED，并重新启用字典行。
--
-- Manual rollback required: V1105 的 UPDATE 将存量 CANCELLED 数据归一为 TODO，
-- 该归一是单向不可逆的——回滚 ENUM 列定义后，原 CANCELLED 任务已变为 TODO，
-- 无法恢复其原始状态分布。回滚仅恢复列定义（允许写入废弃值），不恢复数据。
-- 如需完全回滚，需从备份中恢复 tasks 表数据。

-- 恢复 tasks.status ENUM 列定义
ALTER TABLE tasks MODIFY COLUMN status enum ('TODO','IN_PROGRESS','REVIEW','COMPLETED','CANCELLED') NOT NULL;

-- 重新启用状态字典中的 IN_PROGRESS 行
UPDATE task_status_dict SET enabled = TRUE WHERE code = 'IN_PROGRESS';
