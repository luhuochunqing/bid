-- V1069__add_result_summary_to_warehouse_export_task.sql
-- PR: (待补)
-- 蓝图追溯: 4.4.1.4 批量导入导出 - 仓库导出包统计 (行数/附件分类数/包大小/耗时/筛选摘要)
ALTER TABLE warehouse_export_task
    ADD COLUMN result_summary TEXT NULL COMMENT '导出包统计 JSON：totalCount/zipBytes/分类附件数/耗时/筛选摘要';
