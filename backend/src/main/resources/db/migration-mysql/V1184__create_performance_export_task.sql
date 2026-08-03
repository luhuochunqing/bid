-- V1184: 业绩合订本导出任务表
-- 需求：业绩合订本导出功能（异步任务 + Word 文档四级标题 + 央企共享优化）
-- 对标 warehouse_export_task（V1032 + V1069），独立表避免与仓库导出任务混杂。
CREATE TABLE IF NOT EXISTS performance_export_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    status ENUM('PENDING','PROCESSING','COMPLETED','FAILED') NOT NULL,
    filter_snapshot TEXT NULL COMMENT '导出筛选条件 JSON：ids/筛选条件/attachmentTypes',
    total_count INT NULL COMMENT '导出业绩记录数',
    stored_file_path VARCHAR(500) NULL COMMENT '导出文件落盘绝对路径',
    download_url VARCHAR(500) NULL COMMENT '下载相对 URL',
    expires_at DATETIME NULL COMMENT '文件过期时间（默认 24h）',
    created_by BIGINT NOT NULL COMMENT '创建人用户 ID',
    created_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    failure_reason VARCHAR(500) NULL,
    result_summary TEXT NULL COMMENT '导出统计 JSON：totalCount/wordBytes/elapsedMs/筛选摘要'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业绩合订本导出任务';
