-- V1220: Fix CHAR->VARCHAR mismatch introduced by V1219 (ddl-auto=validate blocker).
-- V1219 用 CHAR(64) 定义了 fingerprint 列，但 Hibernate 实体 @Column(length=64) 期望 varchar(64)。
-- 生产/测试 profile 均启用 ddl-auto=validate，若不修正，后端启动时 schema-validation 直接失败。
-- 实测报错: wrong column type encountered in column [projection_fingerprint] ... found [char (Types#CHAR)], expecting [varchar(64)]
ALTER TABLE score_active_version
    MODIFY COLUMN projection_fingerprint VARCHAR(64) NOT NULL COMMENT '生效时完整投影摘要';
ALTER TABLE score_item
    MODIFY COLUMN rule_fingerprint VARCHAR(64) NULL COMMENT '完整规则摘要';
