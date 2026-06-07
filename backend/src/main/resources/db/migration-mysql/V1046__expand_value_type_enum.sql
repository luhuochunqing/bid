-- V1046: 扩展 tender_evaluation_customer_info.value_type 枚举值
-- Java ValueType 枚举已扩展为 TEXT/DROPDOWN/SWITCH/ENUM14/ENUM7/DROPDOWN6
-- V130 仅定义了 TEXT 和 DROPDOWN，需补齐新增的 4 个枚举值
ALTER TABLE tender_evaluation_customer_info
  MODIFY COLUMN value_type
    ENUM('TEXT','DROPDOWN','SWITCH','ENUM14','ENUM7','DROPDOWN6')
    NOT NULL DEFAULT 'TEXT'
    COMMENT '值类型';
