-- V1035: 复盘页蓝图字段补齐 (PRD §3.3.1.5)
-- 使用存储过程实现幂等
-- 新增：会议信息（时间/形式/参与人）、丢标原因多选标记、中标/未中标拆分字段、复盘报告附件
-- 先将已有的大 VARCHAR 升级为 TEXT 释放行宽，再新增字段
-- 旧字段保留兼容：loss_reasons（改为文本备注），improvement_actions（中标侧保留）

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_not_exists$$
CREATE PROCEDURE add_column_if_not_exists(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_def VARCHAR(500)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table_name,
            ' ADD COLUMN ', p_column_name, ' ', p_column_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS run_v1035_migration$$
CREATE PROCEDURE run_v1035_migration()
BEGIN
    -- 先释放行宽：将已有大 VARCHAR 列升级为 TEXT（TEXT 不占行宽配额）
    ALTER TABLE project_retrospective MODIFY COLUMN summary TEXT;
    ALTER TABLE project_retrospective MODIFY COLUMN win_factors TEXT;
    ALTER TABLE project_retrospective MODIFY COLUMN loss_reasons TEXT;
    ALTER TABLE project_retrospective MODIFY COLUMN competitor_notes TEXT;
    ALTER TABLE project_retrospective MODIFY COLUMN improvement_actions TEXT;
    ALTER TABLE project_retrospective MODIFY COLUMN review_comment TEXT;

    -- 会议信息
    CALL add_column_if_not_exists('project_retrospective', 'meeting_time', 'DATETIME');
    CALL add_column_if_not_exists('project_retrospective', 'meeting_format', 'VARCHAR(20)');
    CALL add_column_if_not_exists('project_retrospective', 'meeting_participants', 'VARCHAR(500)');

    -- 丢标原因多选标记
    CALL add_column_if_not_exists('project_retrospective', 'loss_reason_flags', 'VARCHAR(255)');

    -- 中标后续改进建议
    CALL add_column_if_not_exists('project_retrospective', 'post_win_improvements', 'TEXT');

    -- 流程存在问题（未中标）
    CALL add_column_if_not_exists('project_retrospective', 'process_problems', 'TEXT');

    -- 具体改进措施（未中标）
    CALL add_column_if_not_exists('project_retrospective', 'post_loss_measures', 'TEXT');

    -- 复盘报告附件ID（TEXT 避免行宽超限）
    CALL add_column_if_not_exists('project_retrospective', 'report_file_ids', 'TEXT');
END$$

DELIMITER ;

CALL run_v1035_migration();
DROP PROCEDURE IF EXISTS run_v1035_migration;
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
