-- CO-362: personnel_certificate / personnel_education 表缺失 V1065/V1066 新增列
--
-- 根因：V1065（personnel_extensions）和 V1066（add_personnel_remark_fields）在
-- flyway_schema_history 中标记为 success=1（2026-06-12 执行），但服务器数据库
-- personnel_certificate / personnel_education 表实际并未新增对应列。推测是该次
-- 部署后数据库从较早备份恢复，导致 schema 与 Flyway 历史不一致。
--
-- 现象：投标专员（bid-Team）权限放开后访问 GET /api/knowledge/personnel，Hibernate
-- 查询 personnel_certificate 时报 Unknown column 'is_permanent' in 'field list'，
-- GlobalExceptionHandler 兜底为 500。
--
-- 修复：用幂等存储过程补齐缺失列。Flyway 不会重跑已 success 的 V1065/V1066，
-- 因此必须用新版本号 V1103。MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，
-- 改用 information_schema 判断后再 ALTER，保证可重复执行。

DELIMITER $$
DROP PROCEDURE IF EXISTS repair_personnel_missing_columns$$
CREATE PROCEDURE repair_personnel_missing_columns()
BEGIN
    -- personnel_certificate.title (V1065)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'personnel_certificate'
                     AND column_name = 'title') THEN
        ALTER TABLE personnel_certificate ADD COLUMN title VARCHAR(50);
    END IF;

    -- personnel_certificate.is_permanent (V1065)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'personnel_certificate'
                     AND column_name = 'is_permanent') THEN
        ALTER TABLE personnel_certificate ADD COLUMN is_permanent BOOLEAN DEFAULT FALSE;
    END IF;

    -- personnel_certificate.remark (V1066)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'personnel_certificate'
                     AND column_name = 'remark') THEN
        ALTER TABLE personnel_certificate ADD COLUMN remark VARCHAR(500);
    END IF;

    -- personnel_education.is_highest_education_school (V1065)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'personnel_education'
                     AND column_name = 'is_highest_education_school') THEN
        ALTER TABLE personnel_education ADD COLUMN is_highest_education_school BOOLEAN DEFAULT FALSE;
    END IF;
END$$
DELIMITER ;

CALL repair_personnel_missing_columns();
DROP PROCEDURE IF EXISTS repair_personnel_missing_columns;
