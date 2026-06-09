-- V1067: 仓库表增加 updated_by 字段
ALTER TABLE warehouse ADD COLUMN updated_by BIGINT COMMENT '更新人ID';
-- PR: #367
