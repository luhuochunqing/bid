-- Input: V1136__warehouse_attachment_type_to_varchar.sql
-- Output: U1136 rollback script for warehouse_attachment type column type
-- Pos: db/rollback/migration-mysql/
--
-- Backout strategy: V1136 把 type 列从 enum('PROPERTY_CERTIFICATE','INVOICE','PHOTOS')
-- 改成了 VARCHAR(30)，以容纳 LEASE_CONTRACT 等新枚举值。
-- 回退需要把列改回 enum，但 enum 不接受 LEASE_CONTRACT，所以必须先确保
-- 没有 LEASE_CONTRACT 数据存在，否则回滚会失败。
--
-- 注意：回滚前请确保 warehouse_attachment 表中没有 type = 'LEASE_CONTRACT' 的数据，
-- 否则 ALTER TABLE MODIFY COLUMN 会因 Data truncated 而失败。
-- 如有 LEASE_CONTRACT 数据，请先手动处理（删除或映射为其他类型）再执行回滚。
--
-- 仅在紧急回滚 V1136 时使用，回滚后租赁合同附件上传将重新报 500（恢复到 V1136 前的状态）。

-- Pre-flight: count rows that carry non-enum values, for the rollback log.
SELECT COUNT(*) AS rows_with_lease_contract
  FROM warehouse_attachment
 WHERE type = 'LEASE_CONTRACT';

-- 注意：如果上面的查询结果 > 0，下面的 ALTER TABLE 会失败。
-- 请先处理这些数据再继续回滚。

-- Restore the original enum type from B73 baseline.
ALTER TABLE warehouse_attachment
    MODIFY COLUMN type ENUM('PROPERTY_CERTIFICATE','INVOICE','PHOTOS') NOT NULL;
