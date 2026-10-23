-- Input: AI案例沉淀任务状态需要跨请求、跨进程持久化并发门禁
-- Output: case_precipitation_task 任务审计表及 project/status 查询索引
-- Pos: casework 模块案例沉淀后台任务状态机持久化

CREATE TABLE case_precipitation_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id CHAR(36) NOT NULL,
    project_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    stage VARCHAR(50) NULL,
    trigger_user_id BIGINT NULL,
    case_count INT NULL,
    user_error_message VARCHAR(255) NULL,
    started_at DATETIME(6) NULL,
    heartbeat_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_case_precipitation_task_id (task_id),
    KEY idx_case_precipitation_project_latest (project_id, id),
    KEY idx_case_precipitation_active (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI案例沉淀后台任务';
