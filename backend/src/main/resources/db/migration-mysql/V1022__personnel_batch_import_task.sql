-- V1021__personnel_batch_import_task.sql
-- 为「批量导入导出」h5 增加人员证书批量导入任务持久化支持
-- 支持异步导入、结果报告、修正文件下载等核心能力

CREATE TABLE personnel_import_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_no VARCHAR(50) NOT NULL UNIQUE COMMENT '任务编号，前端展示用（如 IMP-20260530-001）',
    module VARCHAR(50) NOT NULL DEFAULT 'PERSONNEL_CERTIFICATE' COMMENT '导入模块，当前固定为人员证书',
    status VARCHAR(20) NOT NULL COMMENT 'PENDING / PROCESSING / COMPLETED / PARTIAL_SUCCESS / FAILED',
    total_count INT DEFAULT 0 COMMENT '总记录数（基础信息行数）',
    success_count INT DEFAULT 0,
    failure_count INT DEFAULT 0,
    warning_count INT DEFAULT 0 COMMENT '姓名交叉校验等警告数量',
    result_summary JSON COMMENT '整体结果摘要（成功/失败统计 + 简要信息）',
    error_details JSON COMMENT '详细错误列表（每行错误原因、Sheet、行号等）',
    correction_file_url VARCHAR(500) COMMENT '修正文件下载链接（仅含失败行的3-Sheet Excel）',
    export_zip_url VARCHAR(500) COMMENT '导出ZIP链接（仅导出场景复用）',
    created_by BIGINT COMMENT '操作人ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    INDEX idx_task_module_status (module, status),
    INDEX idx_task_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人员证书批量导入任务表（支持异步 + 详细报告）';
