-- V1034: 结果确认竞争对手情况表 (PRD §3.3.1.4)
-- 每个 project_result 可关联多条竞争对手记录（默认 3 行，支持动态增删）
-- 字段：竞争对手名称、折扣、账期、其他说明，均为非必填
CREATE TABLE IF NOT EXISTS project_result_competitor (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    result_id     BIGINT       NOT NULL COMMENT 'FK → project_result.id',
    name          VARCHAR(200) NULL     COMMENT '竞争对手名称',
    discount      VARCHAR(100) NULL     COMMENT '折扣，如：95折',
    payment_term  VARCHAR(100) NULL     COMMENT '账期，如：月结60天',
    notes         VARCHAR(500) NULL     COMMENT '其他说明',
    sort_order    INT          NOT NULL DEFAULT 0 COMMENT '排序',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_result_competitor_result
        FOREIGN KEY (result_id) REFERENCES project_result(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    INDEX idx_result_competitor_result (result_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结果确认-竞争对手情况';
