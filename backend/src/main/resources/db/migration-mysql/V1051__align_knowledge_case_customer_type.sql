-- V1051: 对齐 knowledge_case 客户类型枚举值至蓝图规格
-- 蓝图 4.1.1.2: 政府机关/事业单位/高校、央企、地方国企、民企、港澳台及外企
-- 旧值映射: STATE_OWNED→CENTRAL_SOE, PRIVATE→PRIVATE, FOREIGN→FOREIGN_ENTERPRISE, GOVERNMENT→GOVERNMENT

UPDATE knowledge_case SET customer_type = 'CENTRAL_SOE' WHERE customer_type = 'STATE_OWNED';
UPDATE knowledge_case SET customer_type = 'FOREIGN_ENTERPRISE' WHERE customer_type = 'FOREIGN';
UPDATE knowledge_case SET customer_type = 'LOCAL_SOE' WHERE customer_type = 'LOCAL_SOE' OR customer_type = '地方国企';
UPDATE knowledge_case SET customer_type = 'GOVERNMENT' WHERE customer_type = 'GOVERNMENT' OR customer_type = '政府机关';
UPDATE knowledge_case SET customer_type = 'PRIVATE' WHERE customer_type = 'PRIVATE' OR customer_type = '民企';
