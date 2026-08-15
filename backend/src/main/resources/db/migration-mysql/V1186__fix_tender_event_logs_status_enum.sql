-- V1186: 修复 tender_event_logs.status 列类型漂移
-- 背景: V1185 建表时 status 用 VARCHAR(20)，但实体 TenderEventLogEntity.status 用
--       @Enumerated(EnumType.STRING) + enum TenderEventStatus {SENT, FAILED}。
--       Hibernate 6 + @Enumerated(EnumType.STRING) 在 ddl-auto=validate 时期望 MySQL ENUM 类型
--       （参考 V1113/V1117 修复模式）。
--       当前 VARCHAR(20) 导致 Schema-validation 失败：found [varchar], expecting [enum]。
--       生产 (application-prod.yml) 也用 ddl-auto=validate，重启会触发同样失败。
-- 修复策略: 参考 V1113 (account_borrow_applications) 模式，将 VARCHAR(20) 改为 ENUM，
--          取值与 TenderEventStatus 对齐（SENT/FAILED）。
-- 风险: 低。应用只写 SENT/FAILED，ENUM 取值与现有数据一致，无需数据清洗。
--       MySQL 8.0 ENUM 修改是 INSTANT 元数据操作，不锁表。

ALTER TABLE tender_event_logs
    MODIFY COLUMN status
    ENUM('SENT','FAILED') NOT NULL DEFAULT 'SENT' COMMENT '发送结果：SENT/FAILED';