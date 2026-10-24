-- Input: V1222 建表时 task_id 为 CHAR(36)，与实体 CasePrecipitationTask 的 varchar(36) 映射不一致，Hibernate 启动校验失败
-- Output: task_id 改为 VARCHAR(36)，与 V1153/V1187 等既有 UUID 字段约定一致
-- Pos: casework 模块案例沉淀后台任务表列类型纠正

ALTER TABLE case_precipitation_task
    MODIFY COLUMN task_id VARCHAR(36) NOT NULL COMMENT 'AI案例沉淀任务 ID（UUID）';
