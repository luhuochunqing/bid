-- V1113: Fix account_borrow_applications.status column type for MySQL ENUM validation
-- 背景: V1071 创建表时 status 列用 VARCHAR(30)，但 Hibernate 6 + @Enumerated(EnumType.STRING)
--       在 ddl-auto=validate 时期望 MySQL ENUM 类型。
--       生产 (application-prod.yml) 也用 ddl-auto=validate，重启会触发同样失败。
--       此问题由 PlatformAccountBorrowServiceMysqlIntegrationTest (MySQL 集成测试) 抓出。
-- 修复策略: 参考 V1014 (brand_auth_attachment) 和 V1111 (platform_accounts) 的修复模式，
--          将 VARCHAR(30) 改为 ENUM，取值与 AccountBorrowApplication.BorrowStatus 对齐。
-- 风险: 低。ENUM 取值与现有数据一致（PENDING_APPROVAL/BORROWED/REJECTED/RETURNED/CANCELLED），
--       MySQL 8.0 INSTANT 元数据操作，不锁表。

ALTER TABLE account_borrow_applications
    MODIFY COLUMN status
    ENUM('PENDING_APPROVAL','BORROWED','REJECTED','RETURNED','CANCELLED')
    NOT NULL;
