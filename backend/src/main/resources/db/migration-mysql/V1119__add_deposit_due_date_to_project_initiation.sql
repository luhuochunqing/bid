-- V1119: 为 project_initiation_details 新增"保证金缴纳截止日期"字段
-- 用途：立项审核通过后自动创建"缴纳投标保证金"任务时，作为任务的 dueDate 来源
-- 语义：可空；用户在立项时手动录入；为空时自动创建的任务无截止日期
-- 来源：用户改造需求 - 保证金任务截止时间从硬编码 +7天 改为读取真实截止日期

ALTER TABLE project_initiation_details
    ADD COLUMN deposit_due_date DATETIME NULL
    COMMENT '保证金缴纳截止日期（用于自动创建缴纳保证金任务的截止时间，可空）'
    AFTER deposit_payment_method;
