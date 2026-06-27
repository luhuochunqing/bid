-- Input: V1105__drop_in_progress_cancelled_status.sql
-- 回滚 V1105: 恢复 task_status_dict 中 IN_PROGRESS 字典行启用状态。
--
-- Manual rollback required: V1105 的 UPDATE 将存量 CANCELLED 数据归一为 TODO，
-- 该归一是单向不可逆的——回滚后原 CANCELLED 任务已变为 TODO，
-- 无法恢复其原始状态分布。如需完全回滚，需从备份中恢复 tasks 表数据。
--
-- 列类型不涉及变更：V1105 不再 ALTER tasks.status（保持 VARCHAR(32) baseline），
-- 因此回滚无需恢复列定义。Task.Status 枚举层仍由代码三态模型约束，
-- 回滚本迁移仅恢复字典行的可见性，不恢复 IN_PROGRESS 作为业务状态的能力。

-- 重新启用状态字典中的 IN_PROGRESS 行
UPDATE task_status_dict SET enabled = TRUE WHERE code = 'IN_PROGRESS';
