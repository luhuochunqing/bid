-- V1199: knowledge_case 去重唯一键从"标题"改为"评分项 ID"
-- 背景：同一项目可存在多个标题相同的评分项（如 1350 的 61/62 均叫"投标报价"），
-- 旧唯一键 (source_project_id, scoring_point_title) 会让 saveAll 整批撞唯一键回滚，
-- 导致案例沉淀全部失败却发成功通知（spec 049 验收发现）。

-- 1. 新增评分项 ID 列（存量行为 NULL，唯一索引允许多个 NULL，不影响历史数据）
ALTER TABLE knowledge_case
ADD COLUMN scoring_point_id BIGINT NULL COMMENT '来源评分项 ID（同标题多评分项的去重依据）' AFTER scoring_point_title;

-- 2. 替换唯一索引：按评分项 ID 幂等去重，同一项目同标题不同评分项不再误冲突
ALTER TABLE knowledge_case
DROP INDEX uk_source_proj_scoring_point,
ADD UNIQUE INDEX uk_source_proj_scoring_point (source_project_id, scoring_point_id);
