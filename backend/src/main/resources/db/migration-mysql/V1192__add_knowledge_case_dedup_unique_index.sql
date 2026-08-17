-- V1192: 为 knowledge_case 添加 (source_project_id, scoring_point_title) 唯一索引，防止案例沉淀重复

-- 1. 清理可能存在的历史重复数据：保留每组 (source_project_id, scoring_point_title) 中 id 最大的记录，删除其余重复项
DELETE k1 FROM knowledge_case k1
INNER JOIN knowledge_case k2
ON k1.source_project_id = k2.source_project_id
AND k1.scoring_point_title = k2.scoring_point_title
AND k1.id < k2.id;

-- 2. 添加唯一复合索引
ALTER TABLE knowledge_case
ADD UNIQUE INDEX uk_source_proj_scoring_point (source_project_id, scoring_point_title);
