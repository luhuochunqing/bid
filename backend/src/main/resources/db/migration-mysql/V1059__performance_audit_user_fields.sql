-- V1054: Change created_by to username and add updated_by for audit tracking
-- §4.5.1 #2/5 业绩管理 — audit user fields for performance records

ALTER TABLE performance_record
    MODIFY COLUMN created_by VARCHAR(100) COMMENT '创建人（用户名）';

ALTER TABLE performance_record
    ADD COLUMN updated_by VARCHAR(100) DEFAULT NULL COMMENT '最后更新人（用户名）';
