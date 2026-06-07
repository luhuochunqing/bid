-- V99: 投标项目 6 阶段全流程基础表 + projects.stage 列
-- PRD §3.1/§3.3/§3.4/§3.5/§3.6 + §5.4 FSM
-- 既有 projects.status 保留兼容；stage 为新阶段口径。
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

DROP PROCEDURE IF EXISTS run_v108_migration$$
CREATE PROCEDURE run_v108_migration()
BEGIN
    -- projects.stage 列及索引
    CALL add_column_if_not_exists('projects', 'stage', "VARCHAR(32) NOT NULL DEFAULT 'INITIATED'");
    CALL add_index_if_not_exists('projects', 'idx_projects_stage', 'ON projects(stage)');

    -- §3.1 立项详情
    CREATE TABLE IF NOT EXISTS project_initiation_details (
        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        project_id BIGINT NOT NULL,
        bid_open_time TIMESTAMP,
        bid_month VARCHAR(16),
        expected_bidders INT,
        customer_type VARCHAR(64),
        annual_revenue DECIMAL(20, 2),
        contract_period_months INT,
        competitors VARCHAR(1024),
        department_snapshot VARCHAR(255),
        deposit_amount DECIMAL(20, 2),
        deposit_payment_method VARCHAR(64),
        locked BOOLEAN NOT NULL DEFAULT FALSE,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_by BIGINT,
        updated_by BIGINT,
        CONSTRAINT uk_initiation_project UNIQUE (project_id)
    );
    CALL add_index_if_not_exists('project_initiation_details', 'idx_initiation_project', 'ON project_initiation_details(project_id)');

    -- §3.3 评标
    CREATE TABLE IF NOT EXISTS project_evaluation (
        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        project_id BIGINT NOT NULL,
        sub_stage VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
        evaluation_started_at TIMESTAMP,
        board_received_at TIMESTAMP,
        announced_at TIMESTAMP,
        notes VARCHAR(2048),
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_by BIGINT,
        updated_by BIGINT,
        CONSTRAINT uk_evaluation_project UNIQUE (project_id)
    );
    CALL add_index_if_not_exists('project_evaluation', 'idx_evaluation_project', 'ON project_evaluation(project_id)');

    -- §3.4 结果确认
    CREATE TABLE IF NOT EXISTS project_result (
        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        project_id BIGINT NOT NULL,
        result_type VARCHAR(16) NOT NULL,
        award_amount DECIMAL(20, 2),
        contract_start_date DATE,
        contract_end_date DATE,
        evidence_attachment_id BIGINT,
        registered_at TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_by BIGINT,
        updated_by BIGINT,
        CONSTRAINT uk_result_project UNIQUE (project_id)
    );
    CALL add_index_if_not_exists('project_result', 'idx_result_project', 'ON project_result(project_id)');
    CALL add_index_if_not_exists('project_result', 'idx_result_type', 'ON project_result(result_type)');

    -- §3.5 复盘
    CREATE TABLE IF NOT EXISTS project_retrospective (
        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        project_id BIGINT NOT NULL,
        summary VARCHAR(4000),
        win_factors VARCHAR(2048),
        loss_reasons VARCHAR(2048),
        competitor_notes VARCHAR(2048),
        improvement_actions VARCHAR(2048),
        reviewed_by BIGINT,
        reviewed_at TIMESTAMP,
        review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_by BIGINT,
        updated_by BIGINT,
        CONSTRAINT uk_retrospective_project UNIQUE (project_id)
    );
    CALL add_index_if_not_exists('project_retrospective', 'idx_retrospective_project', 'ON project_retrospective(project_id)');

    -- §3.6 结项（含 stage_locked 全字段锁定标记）
    CREATE TABLE IF NOT EXISTS project_closure (
        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        project_id BIGINT NOT NULL,
        closed_at TIMESTAMP,
        closed_by BIGINT,
        deposit_returned BOOLEAN NOT NULL DEFAULT FALSE,
        deposit_return_evidence_id BIGINT,
        archive_location VARCHAR(512),
        stage_locked BOOLEAN NOT NULL DEFAULT FALSE,
        notes VARCHAR(2048),
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_by BIGINT,
        updated_by BIGINT,
        CONSTRAINT uk_closure_project UNIQUE (project_id)
    );
    CALL add_index_if_not_exists('project_closure', 'idx_closure_project', 'ON project_closure(project_id)');
    CALL add_index_if_not_exists('project_closure', 'idx_closure_locked', 'ON project_closure(stage_locked)');

    -- 清理存储过程（注意：不能在存储过程内 DROP 其他存储过程）
    -- 统一在外层清理
END$$

DELIMITER ;

CALL run_v108_migration();
DROP PROCEDURE IF EXISTS run_v108_migration;
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
