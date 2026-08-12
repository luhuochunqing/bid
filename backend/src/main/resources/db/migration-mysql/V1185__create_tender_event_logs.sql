-- 标讯推送西域消息队列：事件流水表
-- 记录每条标讯事件的追踪信息（traceId/spanId/parentId）与发送结果，用于问题定位。
CREATE TABLE tender_event_logs (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tender_id     BIGINT       NOT NULL COMMENT '标讯 ID',
    event_code    VARCHAR(100) NOT NULL COMMENT '事件编码',
    event_source  VARCHAR(100) NOT NULL COMMENT '事件来源系统',
    event_topic   VARCHAR(100) NOT NULL COMMENT '事件主题',
    trace_id      VARCHAR(128) NOT NULL COMMENT '链路追踪 ID',
    span_id       VARCHAR(128) NOT NULL COMMENT '链路 spanId',
    parent_id     VARCHAR(128) DEFAULT '0' COMMENT '父级链路 ID',
    event_content TEXT         NULL COMMENT '事件消息体（data，仅关键标识）',
    status        VARCHAR(20)  NOT NULL DEFAULT 'SENT' COMMENT '发送结果：SENT/FAILED',
    error_message VARCHAR(1000) NULL COMMENT '失败原因',
    created_at    DATETIME     NOT NULL COMMENT '创建时间',
    INDEX idx_tender_event_logs_tender (tender_id),
    INDEX idx_tender_event_logs_status (status),
    INDEX idx_tender_event_logs_trace (trace_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='标讯事件推送流水表';