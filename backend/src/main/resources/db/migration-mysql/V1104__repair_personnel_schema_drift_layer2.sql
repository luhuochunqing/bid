-- CO-363: personnel 模块 schema 漂移第二层修复
--
-- 背景：V1103 修复了 V1065/V1066 漂移的 4 列（title/is_permanent/remark/is_highest_education_school），
-- 但遗漏了同一批次漂移的其他 schema 变更，导致 POST /api/knowledge/personnel 仍然 500。
--
-- 洋葱型 bug 分析：
--   第一层（403）：PersonnelController 类级+方法级 @PreAuthorize AND 阻塞 → PR #1158/#1191 修复
--   第二层（500-a）：V1065/V1066 的 ALTER TABLE 漂移，4 列缺失 → V1103 修复
--   第三层（500-b）：V1065/V1066 的其他 ALTER TABLE 漂移，personnel.birth_date / personnel.remark 缺失 → 本脚本修复
--   第四层（500-c）：V1020 的 ALTER TABLE 可能同批漂移，personnel_certificate.deleted_at 缺失 → 本脚本修复
--   第五层（500-d）：V1065 的 CREATE TABLE personnel_operation_log 可能同批漂移，表不存在 → 本脚本修复
--
-- 现象：权限注解修复后 POST 从 403 变为 500。Hibernate INSERT personnel 表时
--   报 Unknown column 'birth_date' / 'remark'，或 INSERT personnel_certificate 时
--   报 Unknown column 'deleted_at'，或 logService.save 时 Table doesn't exist。
--   GlobalExceptionHandler 兜底为 500。
--
-- 修复策略：幂等存储过程，覆盖 V1103 遗漏的全部 schema 变更。

DELIMITER $$
DROP PROCEDURE IF EXISTS repair_personnel_schema_drift_layer2$$
CREATE PROCEDURE repair_personnel_schema_drift_layer2()
BEGIN
    -- ============================================================
    -- 1. personnel.birth_date (V1065 漂移)
    -- ============================================================
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'personnel'
                     AND column_name = 'birth_date') THEN
        ALTER TABLE personnel ADD COLUMN birth_date DATE;
    END IF;

    -- ============================================================
    -- 2. personnel.remark (V1066 漂移)
    -- ============================================================
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'personnel'
                     AND column_name = 'remark') THEN
        ALTER TABLE personnel ADD COLUMN remark VARCHAR(500);
    END IF;

    -- ============================================================
    -- 3. personnel_certificate.deleted_at (V1020 可能同批漂移)
    -- ============================================================
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'personnel_certificate'
                     AND column_name = 'deleted_at') THEN
        ALTER TABLE personnel_certificate ADD COLUMN deleted_at DATETIME NULL COMMENT '软删除时间，NULL表示未删除';
    END IF;

    -- idx_cert_personnel_active 索引 (V1020)
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'personnel_certificate'
                     AND index_name = 'idx_cert_personnel_active') THEN
        CREATE INDEX idx_cert_personnel_active ON personnel_certificate (personnel_id, deleted_at);
    END IF;

    -- ============================================================
    -- 4. personnel_operation_log 表 (V1065 CREATE TABLE 可能漂移)
    -- ============================================================
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = DATABASE()
                     AND table_name = 'personnel_operation_log') THEN
        CREATE TABLE personnel_operation_log (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            personnel_id BIGINT NOT NULL,
            operator_id BIGINT NOT NULL,
            operator_name VARCHAR(100),
            operation_type VARCHAR(50) NOT NULL,
            change_details JSON,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_personnel_id (personnel_id),
            INDEX idx_created_at (created_at)
        );
    ELSE
        -- 表已存在，补齐可能缺失的列
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = DATABASE()
                         AND table_name = 'personnel_operation_log'
                         AND column_name = 'operator_name') THEN
            ALTER TABLE personnel_operation_log ADD COLUMN operator_name VARCHAR(100);
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = DATABASE()
                         AND table_name = 'personnel_operation_log'
                         AND column_name = 'change_details') THEN
            ALTER TABLE personnel_operation_log ADD COLUMN change_details JSON;
        END IF;
    END IF;
END$$
DELIMITER ;

CALL repair_personnel_schema_drift_layer2();
DROP PROCEDURE IF EXISTS repair_personnel_schema_drift_layer2;
