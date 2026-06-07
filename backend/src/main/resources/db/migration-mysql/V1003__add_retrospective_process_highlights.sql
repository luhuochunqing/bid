-- ================================================================
-- V1003: Add process_highlights column to project_retrospective
-- 功能：为项目复盘表增加"流程亮点"字段，对齐产品蓝图 §4.3.3 复盘字段。
-- ================================================================

ALTER TABLE project_retrospective
    ADD COLUMN process_highlights TEXT DEFAULT NULL
    COMMENT '流程亮点（中标时填写）';
