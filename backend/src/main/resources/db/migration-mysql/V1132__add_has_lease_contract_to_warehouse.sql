-- V1132: 仓库表增加 has_lease_contract 字段（租赁合同附件标识）
ALTER TABLE warehouse ADD COLUMN has_lease_contract TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否有租赁合同';
-- PR: CO-493
