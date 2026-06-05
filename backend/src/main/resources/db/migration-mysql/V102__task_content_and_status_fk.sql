-- V102: Task 新增 content 富文本字段；status 列由 ENUM 改为 VARCHAR(32)
-- 设计说明：
--   1) 不加到 task_status_dict.code 的硬 FK。原因：未来字典管理页禁用某状态时，
--      历史 tasks 行仍引用该 code；service 层 + ArchitectureTest 负责守卫。
--   2) 历史值 TODO/IN_PROGRESS/REVIEW/COMPLETED 对应 V101 种子；CANCELLED 不在
--      字典中，列类型切换后仍以字符串 'CANCELLED' 保留在表中，未来由字典管理
--      页或数据修复脚本统一处理（保留、补登字典或归档），当前 V102 不做回填。
--   3) 新增 content 用 TEXT（64KB）而非 MEDIUMTEXT；Markdown 任务描述 64KB 绰绰
--      有余，应用层再加长度校验即可。
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

DROP PROCEDURE IF EXISTS modify_column_if_type_matches$$
CREATE PROCEDURE modify_column_if_type_matches(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_target_type_pattern VARCHAR(64),
    IN p_new_def VARCHAR(1000)
)
BEGIN
    DECLARE v_col_type VARCHAR(128) DEFAULT NULL;
    SELECT COLUMN_TYPE INTO v_col_type
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = p_column_name;
    IF v_col_type IS NOT NULL AND v_col_type LIKE p_target_type_pattern THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table_name, ' MODIFY COLUMN ', p_column_name, ' ', p_new_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS run_v102_migration$$
CREATE PROCEDURE run_v102_migration()
BEGIN
    -- tasks.content 列
    CALL add_column_if_not_exists('tasks', 'content', "TEXT NULL COMMENT '任务详细描述（Markdown 文本，上限 64KB）'");

    -- status ENUM -> VARCHAR(32)：仅当仍为 ENUM 类型时才修改
    CALL modify_column_if_type_matches(
        'tasks', 'status', 'enum%',
        'VARCHAR(32) NOT NULL'
    );

    -- 索引：幂等创建
    CALL add_index_if_not_exists('tasks', 'idx_tasks_status', 'ON tasks (status)');
    CALL add_index_if_not_exists('tasks', 'idx_tasks_project_status', 'ON tasks (project_id, status)');

    -- 清理存储过程（注意：不能在存储过程内 DROP 其他存储过程）
    -- 统一在外层清理
END$$

DELIMITER ;

CALL run_v102_migration();
DROP PROCEDURE IF EXISTS run_v102_migration;
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
DROP PROCEDURE IF EXISTS modify_column_if_type_matches;
