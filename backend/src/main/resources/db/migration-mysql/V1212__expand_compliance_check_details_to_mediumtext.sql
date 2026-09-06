-- V1212: expand_compliance_check_details_to_mediumtext
-- 说明: 将 compliance_check_results.check_details 字段由 TEXT 扩容至 MEDIUMTEXT（最大支持 16MB），
-- 避免大标书/多册招标文件 25 项深度核查结果 JSON 超过 64KB 导致 Data truncation 错误。

ALTER TABLE compliance_check_results
    MODIFY COLUMN check_details MEDIUMTEXT NULL COMMENT '检查详情（JSON格式，支持大标书长文本详情）';

