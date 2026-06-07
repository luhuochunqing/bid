-- V1044__project_stage_timestamps.sql
-- 添加项目阶段时间戳字段：立项时间、评标时间、结项时间
-- 对应蓝图需求：档案详情页展示真实立项/标书提交/结项时间
-- 仅 initiated_at 回填（历史数据无 evaluating/closed 来源）

ALTER TABLE projects
    ADD COLUMN initiated_at DATETIME NULL COMMENT '立项时间（首次进入INITIATED阶段）',
    ADD COLUMN evaluating_at DATETIME NULL COMMENT '评标时间（首次进入EVALUATING阶段）',
    ADD COLUMN closed_at DATETIME NULL COMMENT '结项时间（首次进入CLOSED阶段）';

-- 历史数据回填：仅有 initiated_at 可追溯（复用 created_at 作为近似值）
-- evaluating_at 和 closed_at 在历史数据中无来源，保持 NULL
UPDATE projects SET initiated_at = created_at WHERE initiated_at IS NULL;
