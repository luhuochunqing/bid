-- Input: 回滚脚本参数、当前 DB 状态
-- Output: 成功删除 tenders.contact_nature / tenders.contact_nature2 列
-- Pos: 与 V1200 配对，作为标讯联系人性质列的回滚
-- 维护声明: 维护者按项目SOP；与 V1200 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest
-- Source: V1200__add_contact_nature_to_tenders.sql

-- U1200 rollback for V1200__add_contact_nature_to_tenders.sql（标讯联系人1/2性质字段）
-- 注意：回滚会丢弃已录入的联系人性质数据，回滚前请确认业务允许。

ALTER TABLE tenders DROP COLUMN contact_nature;
ALTER TABLE tenders DROP COLUMN contact_nature2;
