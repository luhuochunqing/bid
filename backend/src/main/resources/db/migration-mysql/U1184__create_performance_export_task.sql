-- U1184: Rollback performance_export_task table
-- 回滚 V1184：删除业绩合订本导出任务表
-- 注意：回滚前请确认无活跃任务（status IN ('PENDING','PROCESSING')）
--       已完成的导出文件（stored_file_path）需手动清理

DROP TABLE IF EXISTS performance_export_task;
