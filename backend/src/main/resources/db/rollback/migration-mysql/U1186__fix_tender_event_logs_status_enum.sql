-- Input: migration-mysql/V1186__fix_tender_event_logs_status_enum.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1186: 回滚 fix_tender_event_logs_status_enum
-- 恢复到 V1185 的 VARCHAR(20) 定义。
-- 注意: 回滚后 Hibernate 6 + @Enumerated(EnumType.STRING) + ddl-auto=validate 会再次失败
--       （found [varchar], expecting [enum]），仅用于 Flyway 历史迁移覆盖测试，生产不建议回滚。

ALTER TABLE tender_event_logs
    MODIFY COLUMN status
    VARCHAR(20) NOT NULL DEFAULT 'SENT' COMMENT '发送结果：SENT/FAILED';