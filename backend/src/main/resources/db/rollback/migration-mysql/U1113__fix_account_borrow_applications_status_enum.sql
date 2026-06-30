-- Input: V1113__fix_account_borrow_applications_status_enum.sql
-- U1113: Rollback - 恢复 account_borrow_applications.status 为 VARCHAR(30)
-- 关联: V1113, PlatformAccountBorrowServiceMysqlIntegrationTest
-- 注意:
--   1. 回滚后 Hibernate 6 + @Enumerated(EnumType.STRING) + ddl-auto=validate 会再次失败
--      （期望 ENUM，实际 VARCHAR）。回滚必须同时回退到 V1113 之前的代码状态，
--      即放弃 MySQL 集成测试或临时用 ddl-auto=none。
--   2. 回滚风险低：ENUM → VARCHAR 不丢数据（enum 字面量本身就是字符串）。

ALTER TABLE account_borrow_applications
    MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL';
