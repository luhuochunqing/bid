-- V101: 任务状态字典（全平台统一主数据），解耦任务状态的显示与业务判断
-- NOTE: is_initial 的"全表至多一条 true"唯一性由 service 层在写入时校验，
--       因为 MySQL 8 不支持 WHERE 子句的 partial index；生成列 + 唯一索引的
--       变通方案留待"状态字典管理页"上线时再评估，避免现在引入额外复杂度。
CREATE TABLE task_status_dict (
    code         VARCHAR(32)  NOT NULL PRIMARY KEY,
    name         VARCHAR(64)  NOT NULL,
    category     VARCHAR(16)  NOT NULL,
    color        VARCHAR(16)  NOT NULL DEFAULT '#909399',
    sort_order   INT          NOT NULL DEFAULT 0,
    is_initial   BOOLEAN      NOT NULL DEFAULT FALSE,
    is_terminal  BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   BIGINT       NULL,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by   BIGINT       NULL,
    CONSTRAINT ck_task_status_dict_category CHECK (category IN ('OPEN','IN_PROGRESS','REVIEW','CLOSED'))
);

CREATE INDEX idx_task_status_dict_enabled_sort ON task_status_dict (enabled, sort_order);

-- 种子：保持与历史 ENUM 等价的 4 条记录，确保业务语义一致
INSERT INTO task_status_dict (code, name, category, color, sort_order, is_initial, is_terminal, enabled)
VALUES
    ('TODO',        '待办',   'OPEN',        '#909399', 10, TRUE,  FALSE, TRUE),
    ('IN_PROGRESS', '进行中', 'IN_PROGRESS', '#409eff', 20, FALSE, FALSE, TRUE),
    ('REVIEW',      '待审核', 'REVIEW',      '#e6a23c', 30, FALSE, FALSE, TRUE),
    ('COMPLETED',   '已完成', 'CLOSED',      '#67c23a', 40, FALSE, TRUE,  TRUE);
