-- V1020__add_personnel_view_fields.sql
-- 为「查看证书」h5（蓝图 4.3 "查看证书"）补充人员列表 11 列所需字段
-- 对应蓝图：入职时间、入职年限（基于入职时间计算）、性别、手机号码
-- 最高学历从 education 记录动态计算（不再单独存储）

ALTER TABLE personnel
    ADD COLUMN gender VARCHAR(10) NULL COMMENT '性别（男/女），支持列表性别 Tag 展示' AFTER department_name,
    ADD COLUMN entry_date DATE NULL COMMENT '入职日期（YYYY-MM-DD），用于计算入职年限' AFTER gender,
    ADD COLUMN phone VARCHAR(20) NULL COMMENT '手机号码，支持列表展示' AFTER entry_date;

-- 为常用过滤/排序添加索引（入职时间、性别）
CREATE INDEX idx_personnel_entry_date ON personnel (entry_date);
CREATE INDEX idx_personnel_gender ON personnel (gender);
