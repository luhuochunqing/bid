-- Input: 回滚脚本参数、当前 DB 状态
-- Output: 成功删除 project_archive / archive_file / archive_log 表
-- Pos: 与 V1043 配对，作为蓝图 4.1.1.1 项目档案的回滚
-- 维护声明: 维护者按项目SOP；与 V1043 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest
-- Source: V1043__knowledge_base_project_archive_tables.sql

-- U1043 rollback for V1043__knowledge_base_project_archive_tables.sql (知识库 4.1.1.1)

DROP TABLE IF EXISTS archive_log;
DROP TABLE IF EXISTS archive_file;
DROP TABLE IF EXISTS project_archive;
