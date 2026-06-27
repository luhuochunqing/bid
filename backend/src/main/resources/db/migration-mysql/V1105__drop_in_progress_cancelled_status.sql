-- CO-361: V1105 任务三态模型收口——废弃 IN_PROGRESS 和 CANCELLED
-- 业务侧只采用三态：TODO → REVIEW → COMPLETED（审核驳回回 TODO）。
-- V1101 已将存量 IN_PROGRESS 归一为 TODO；本迁移补做 CANCELLED→TODO 归一，
-- 并禁用 task_status_dict 中的 IN_PROGRESS 字典行。
--
-- 列类型保持 VARCHAR(32)（V102 已从 ENUM 改为 VARCHAR(32)，Task.java @Enumerated(EnumType.STRING) 约束合法值），
-- 不再收紧为 ENUM——跟随项目既有趋势（V102/V1102 均为 ENUM→VARCHAR），
-- 避免回滚失真，保留后续新增状态的灵活性。

-- 1. 存量 CANCELLED 任务归一为 TODO（单向不可逆，见 U1105）
UPDATE tasks SET status = 'TODO' WHERE status = 'CANCELLED';

-- 2. 状态字典：禁用 IN_PROGRESS 行（V101 种子数据）
--    保留行记录以维护主数据完整性，仅置为 enabled=FALSE 使其不再出现在前端状态选择器。
UPDATE task_status_dict SET enabled = FALSE WHERE code = 'IN_PROGRESS';
