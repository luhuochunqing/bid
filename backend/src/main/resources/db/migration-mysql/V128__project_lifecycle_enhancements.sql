-- V126: 投标项目全生命周期增强（蓝图 V1.1 §4.3）
-- 注意：summary (project_result, V113 已加) 与 review_status (project_retrospective, V108 已加) 复用现有列
-- 幂等：所有操作全部封装在存储过程中执行

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_not_exists$$
CREATE PROCEDURE add_column_if_not_exists(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_def VARCHAR(1000)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table_name, ' ADD COLUMN ', p_column_name, ' ', p_column_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS add_index_if_not_exists$$
CREATE PROCEDURE add_index_if_not_exists(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_index_def VARCHAR(1000)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND INDEX_NAME = p_index_name
    ) THEN
        SET @sql = CONCAT('CREATE INDEX ', p_index_name, ' ', p_index_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS modify_column_if_type_like$$
CREATE PROCEDURE modify_column_if_type_like(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_type_pattern VARCHAR(64),
    IN p_new_def VARCHAR(1000)
)
BEGIN
    DECLARE v_col_type VARCHAR(128) DEFAULT NULL;
    SELECT COLUMN_TYPE INTO v_col_type
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = p_column_name;
    IF v_col_type IS NOT NULL AND v_col_type LIKE p_type_pattern THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table_name, ' MODIFY COLUMN ', p_column_name, ' ', p_new_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS run_v128_migration$$
CREATE PROCEDURE run_v128_migration()
BEGIN
    -- ============ project_initiation_details: 立项审批字段 ============
    CALL add_column_if_not_exists('project_initiation_details', 'review_status',     "VARCHAR(32) NOT NULL DEFAULT 'DRAFT'");
    CALL add_column_if_not_exists('project_initiation_details', 'rejection_reason',  'TEXT');
    CALL add_column_if_not_exists('project_initiation_details', 'reviewed_by',       'BIGINT');
    CALL add_column_if_not_exists('project_initiation_details', 'reviewed_at',      'DATETIME');
    CALL add_column_if_not_exists('project_initiation_details', 'customer_info_json','JSON');
    CALL add_column_if_not_exists('project_initiation_details', 'tender_document_id','BIGINT');
    CALL add_column_if_not_exists('project_initiation_details', 'ai_risk_level',     'VARCHAR(16)');
    CALL add_index_if_not_exists('project_initiation_details', 'idx_project_initiation_review_status', 'ON project_initiation_details(review_status)');

    -- ============ project_evaluation: 评标文件 + notes NOT NULL ============
    CALL modify_column_if_type_like(
        'project_evaluation', 'notes', 'varchar%',
        "VARCHAR(2048) NOT NULL DEFAULT ''"
    );
    CALL add_column_if_not_exists('project_evaluation', 'evaluation_files_json', 'JSON');

    -- ============ project_result: 多凭证文件 (summary 复用 V113 现有列) ============
    CALL add_column_if_not_exists('project_result', 'evidence_file_ids', 'JSON');

    -- ============ project_retrospective: 会议信息 + 报告附件 (review_status 复用 V108 现有列) ============
    CALL add_column_if_not_exists('project_retrospective', 'meeting_time',        'DATETIME');
    CALL add_column_if_not_exists('project_retrospective', 'meeting_type',        'VARCHAR(16)');
    CALL add_column_if_not_exists('project_retrospective', 'participants',       'VARCHAR(500)');
    CALL add_column_if_not_exists('project_retrospective', 'process_issues',     'TEXT');
    CALL add_column_if_not_exists('project_retrospective', 'report_attachment_id','BIGINT');

    -- ============ project_closure: 审核流程 + 项目总结 ============
    CALL add_column_if_not_exists('project_closure', 'review_status',   "VARCHAR(32) NOT NULL DEFAULT 'DRAFT'");
    CALL add_column_if_not_exists('project_closure', 'reviewed_by',     'BIGINT');
    CALL add_column_if_not_exists('project_closure', 'reviewed_at',     'DATETIME');
    CALL add_column_if_not_exists('project_closure', 'project_summary','TEXT');
    CALL add_index_if_not_exists('project_closure', 'idx_project_closure_review_status', 'ON project_closure(review_status)');

    -- ============ tasks: 驳回原因 ============
    CALL add_column_if_not_exists('tasks', 'review_comment', 'TEXT');

    -- 清理存储过程（注意：不能在存储过程内 DROP 其他存储过程）
    -- 统一在外层清理
END$$

DELIMITER ;

CALL run_v128_migration();
DROP PROCEDURE IF EXISTS run_v128_migration;
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
DROP PROCEDURE IF EXISTS modify_column_if_type_like;
