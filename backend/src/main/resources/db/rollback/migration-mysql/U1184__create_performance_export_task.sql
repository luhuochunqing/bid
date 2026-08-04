-- Input: 回滚脚本参数、当前 DB 状态
-- Output: 成功删除 performance_export_task 表
-- Pos: 与 V1184 配对，作为业绩合订本导出功能的回滚
-- 维护声明: 维护者按项目SOP；与 V1184 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest
-- Source: V1184__create_performance_export_task.sql

-- U1184 rollback for V1184__create_performance_export_task.sql (业绩合订本导出任务表)
-- 注意：回滚前请确认无活跃任务（status IN ('PENDING','PROCESSING')）
--       已完成的导出文件（stored_file_path）需手动清理

DROP TABLE IF EXISTS performance_export_task;
