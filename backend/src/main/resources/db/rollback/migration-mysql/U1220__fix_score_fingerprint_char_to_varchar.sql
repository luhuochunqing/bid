-- Input: migration-mysql/V1220__fix_score_fingerprint_char_to_varchar.sql
-- Output: guarded rollback; reverts VARCHAR back to CHAR (V1219 original state).
-- U1220 rollback for V1220. 注意: 回滚后 projection_fingerprint / rule_fingerprint
--       会回到 CHAR(64)，ddl-auto=validate 下后端启动将再次失败（V1219 原始缺陷）。
--       仅在同时回滚到 V1219 之前的版本（不含 V1219/V1220）时使用。
ALTER TABLE score_active_version
    MODIFY COLUMN projection_fingerprint CHAR(64) NOT NULL COMMENT '生效时完整投影摘要';
ALTER TABLE score_item
    MODIFY COLUMN rule_fingerprint CHAR(64) NULL COMMENT '完整规则摘要';
