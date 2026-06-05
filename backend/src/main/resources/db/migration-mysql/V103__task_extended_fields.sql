-- V103: 任务扩展字段 schema 表 + Task 加扩展字段 JSON 列
-- 设计说明：
--   1) 全平台共享 schema（不做 per-project / per-template）
--   2) task.extended_fields_json 存 {"key":"value", ...}
--   3) key 一旦落库不可改；改 type/label/required/options 都允许
CREATE TABLE task_extended_field (
    `key`         VARCHAR(64)  NOT NULL PRIMARY KEY,
    label         VARCHAR(128) NOT NULL,
    field_type    VARCHAR(32)  NOT NULL,
    required      BOOLEAN      NOT NULL DEFAULT FALSE,
    placeholder   VARCHAR(255),
    options_json  TEXT,
    sort_order    INT          NOT NULL DEFAULT 0,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT ck_task_extended_field_type
        CHECK (field_type IN ('text','textarea','number','date','select'))
);

CREATE INDEX idx_task_extended_field_enabled_sort
    ON task_extended_field (enabled, sort_order);

ALTER TABLE tasks
    ADD COLUMN extended_fields_json TEXT NULL
    COMMENT '扩展字段键值对 JSON, schema 在 task_extended_field 表';
