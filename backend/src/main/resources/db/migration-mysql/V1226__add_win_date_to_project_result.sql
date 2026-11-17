-- 结果确认阶段合同信息新增"中标时间"字段（仅中标时必填，不用回传 CRM 系统）
ALTER TABLE project_result
    ADD COLUMN win_date DATE NULL COMMENT '中标时间，结果确认中标时必填';
