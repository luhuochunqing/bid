-- V1019__personnel_certificate_soft_delete.sql
-- 为支持「编辑证书」子节的附件替换软删除需求，增加 deleted_at 字段
-- 对应蓝图 4.3 "编辑证书"：替换证书附件 → 原附件删除（软删除）

ALTER TABLE personnel_certificate
    ADD COLUMN deleted_at DATETIME NULL COMMENT '软删除时间，NULL表示未删除';

-- 为查询性能添加索引（排除已删除记录的常用查询）
CREATE INDEX idx_cert_personnel_active ON personnel_certificate (personnel_id, deleted_at);
