-- V1102: Alter business_qualifications.status from MySQL ENUM to VARCHAR(32)
-- 根因（CO-358）：B73 baseline 建表时 status 列定义为 enum('VALID','EXPIRING','EXPIRED')，
-- 但 Java 枚举 QualificationStatus 有 5 个值（IN_STOCK / EXPIRING / EXPIRED / RETIRED / VALID）。
-- 下架接口写入 RETIRED 时被 MySQL enum 列拒绝（"Data truncated for column 'status'"）→ 500。
-- V1075 写入 IN_STOCK 同理也会被拒绝（若有 VALID 行存在）。
--
-- 修复：将列类型改为 VARCHAR(32) NOT NULL，对齐实体 @Column(length=32) 与 JPA @Enumerated(STRING)。
-- VARCHAR(32) 容纳所有现有及未来枚举值，Java 枚举仍是唯一源，DB 不再用 enum 重复约束。
-- 数据无损：现有 VALID/EXPIRING/EXPIRED 字符串值在 VARCHAR 列中保持不变。
--
-- 幂等性：MODIFY COLUMN 是幂等的 DDL（重复执行结果相同）。
-- Backout: see db/rollback/migration-mysql/U1102__alter_business_qualifications_status_to_varchar.sql
-- PR: #<pending>

ALTER TABLE business_qualifications
    MODIFY COLUMN status VARCHAR(32) NOT NULL;
