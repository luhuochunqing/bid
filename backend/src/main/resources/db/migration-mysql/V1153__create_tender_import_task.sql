-- V1153__create_tender_import_task.sql
-- 标讯批量导入异步化：持久化异步导入任务状态，支持服务重启后恢复/标记失败
-- 参考: V1022__personnel_batch_import_task.sql（人员证书批量导入范式）
-- 详见: specs/031-tender-import-async-perf/data-model.md

CREATE TABLE tender_import_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL COMMENT '业务任务 ID（UUID），对外暴露防猜测',
    user_id BIGINT NOT NULL COMMENT '发起用户 ID（关联 users.id）',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名（仅记录，不存文件内容）',
    total_rows INT NOT NULL DEFAULT 0 COMMENT 'Excel 总行数（解析后填充）',
    processed_rows INT NOT NULL DEFAULT 0 COMMENT '已处理行数',
    success_count INT NOT NULL DEFAULT 0 COMMENT '成功行数',
    failure_count INT NOT NULL DEFAULT 0 COMMENT '失败行数',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态机: PENDING/PROCESSING/COMPLETED/PARTIAL_SUCCESS/FAILED',
    error_details JSON NULL COMMENT '失败行明细数组（rowNumber/field/errorMessage/tenderTitle）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at DATETIME NULL COMMENT '完成时间（COMPLETED/PARTIAL_SUCCESS/FAILED 时填充）',
    UNIQUE INDEX uk_task_id (task_id),
    INDEX idx_status_updated (status, updated_at) COMMENT '卡死任务扫描',
    INDEX idx_user_created (user_id, created_at) COMMENT '用户任务列表查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标讯批量导入异步任务表（支持异步 + 进度查询 + 卡死恢复）';
