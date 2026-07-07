-- Input: V1145__add_alert_rule_types.sql
-- Data rollback required: no data rollback needed; only enum type is narrowed.
-- CO-523 rollback: 移除 alert_rules.type 枚举中的 PERFORMANCE_EXPIRY/CA_EXPIRY/CA_BORROW_OVERDUE
-- 注意：回滚后若 alert_rules 表中已存在这三个枚举值的记录，ALTER 会失败（需先手动清理）。
ALTER TABLE alert_rules
    MODIFY COLUMN type enum('DEADLINE','BUDGET','RISK','DOCUMENT','QUALIFICATION_EXPIRY','DEPOSIT_RETURN') NOT NULL;
