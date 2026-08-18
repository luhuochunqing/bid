-- V1196: extend_compliance_check_results_for_quality
-- 扩展 compliance_check_results 支持异步任务流转、SHA-256文件哈希缓存与25项质量统计

ALTER TABLE compliance_check_results
    ADD COLUMN task_id VARCHAR(64) NULL COMMENT '异步任务UUID',
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED' COMMENT '任务状态: INIT, PARSING, CHECKING, COMPLETED, FAILED',
    ADD COLUMN progress INT NOT NULL DEFAULT 100 COMMENT '任务进度百分比 (0-100)',
    ADD COLUMN current_step VARCHAR(128) NULL COMMENT '当前处理步骤说明',
    ADD COLUMN error_message VARCHAR(1000) NULL COMMENT '失败错误信息',
    ADD COLUMN bid_file_name VARCHAR(255) NULL COMMENT '投标文件名',
    ADD COLUMN bid_file_hash VARCHAR(64) NULL COMMENT '投标文件SHA-256',
    ADD COLUMN tender_file_hash VARCHAR(64) NULL COMMENT '招标文件SHA-256',
    ADD COLUMN passed_count INT NOT NULL DEFAULT 0 COMMENT '通过项数',
    ADD COLUMN failed_count INT NOT NULL DEFAULT 0 COMMENT '不通过项数',
    ADD COLUMN manual_review_count INT NOT NULL DEFAULT 0 COMMENT '待确认项数',
    ADD COLUMN total_count INT NOT NULL DEFAULT 25 COMMENT '总检查项数',
    ADD INDEX idx_ccr_task_id (task_id),
    ADD INDEX idx_ccr_hashes (bid_file_hash, tender_file_hash);
