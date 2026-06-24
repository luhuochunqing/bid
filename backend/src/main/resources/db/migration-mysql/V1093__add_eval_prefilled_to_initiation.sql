-- Purpose: CO-323 标讯评估表带入项目立项。
--  - project_initiation_details 加 eval_prefilled 列，标记该立项的评估字段是否由标讯评估表带入。
--    proceedToBid 创建立项时若带入评估数据则置 1，前端据此将带入字段设为只读
--    （保证金/招标文件除外，仍可编辑）。

ALTER TABLE project_initiation_details
    ADD COLUMN eval_prefilled TINYINT(1) NOT NULL DEFAULT 0
    COMMENT 'CO-323 评估表带入标记：1=由标讯评估表带入，带入字段只读';
