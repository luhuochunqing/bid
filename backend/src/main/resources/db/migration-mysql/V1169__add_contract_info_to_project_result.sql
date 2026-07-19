-- CO-590: 结果确认阶段新增"合同信息"模块
-- 在 project_result 表新增两列，承接"合同信息"模块的两个字段：
--   1. 项目服务周期（年）：数字，保留 1 位小数（如 3.5）
--   2. 服务周期截止时间：日期
-- 适用于所有结果类型（WON/LOST/FAILED/ABANDONED），由前端 UI 收集并由策略层校验必填
ALTER TABLE project_result
    ADD COLUMN service_period_years DECIMAL(5,1) NULL COMMENT '项目服务周期（年），保留1位小数，CO-590 合同信息模块',
    ADD COLUMN service_period_end_date DATE NULL COMMENT '服务周期截止时间，CO-590 合同信息模块';
