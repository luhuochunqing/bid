-- Input: migration-mysql/V1046__expand_value_type_enum.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- U1046: 回滚 value_type 枚举扩展（将 SWITCH/ENUM14/ENUM7/DROPDOWN6 降级为 TEXT）
UPDATE tender_evaluation_customer_info
  SET value_type = 'TEXT'
  WHERE value_type NOT IN ('TEXT', 'DROPDOWN');

ALTER TABLE tender_evaluation_customer_info
  MODIFY COLUMN value_type
    ENUM('TEXT','DROPDOWN')
    NOT NULL DEFAULT 'TEXT'
    COMMENT '值类型';
