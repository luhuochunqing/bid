-- V1016__performance_blueprint_overhaul.sql
-- 4.5 业绩管理蓝图对齐：以合同为核心的字段模型
-- 保留旧 POC 列（不删数据），新增蓝图要求的所有字段

-- 1. 新增合同基础信息字段，并允许旧 project_name 字段为 NULL 以支持合同为核心的新模式
ALTER TABLE performance_record
    MODIFY COLUMN project_name VARCHAR(200) NULL COMMENT '项目名称',
    ADD COLUMN contract_name VARCHAR(200) NULL COMMENT '合同名称（蓝图字段，将替代 project_name）' AFTER id,
    ADD COLUMN signing_entity VARCHAR(200) NULL COMMENT '签约单位',
    ADD COLUMN group_company VARCHAR(200) NULL COMMENT '集团公司名称',
    ADD COLUMN customer_type VARCHAR(50) NULL COMMENT '客户类型: GOVERNMENT_INSTITUTION/CENTRAL_SOE/LOCAL_SOE/PRIVATE_ENTERPRISE/FOREIGN_HK_MACAO_TW',
    ADD COLUMN industry VARCHAR(100) NULL COMMENT '所属行业（自由文本）',
    ADD COLUMN project_type VARCHAR(50) NULL COMMENT '项目类型: OFFICE/COMPREHENSIVE/CENTRALIZED/INDUSTRIAL/OTHER',
    ADD COLUMN docking_method VARCHAR(50) NULL COMMENT '对接方式: EMALL/PUNCH_OUT/API',
    ADD COLUMN customer_level VARCHAR(50) NULL COMMENT '客户级别: GROUP/SUBSIDIARY';

-- 2. 新增合同关键日期字段
ALTER TABLE performance_record
    ADD COLUMN signing_date DATE NULL COMMENT '签约日期' AFTER customer_level,
    ADD COLUMN expiry_date DATE NULL COMMENT '截止日期' AFTER signing_date,
    ADD COLUMN total_expiry_date DATE NULL COMMENT '总截止日期（含可续约期）' AFTER expiry_date;

-- 3. 新增客户与联系人字段
ALTER TABLE performance_record
    ADD COLUMN contact_person VARCHAR(100) NULL COMMENT '客户联系人（姓名+职务）' AFTER total_expiry_date,
    ADD COLUMN contact_info VARCHAR(200) NULL COMMENT '客户联系方式（手机/固话/邮箱）' AFTER contact_person,
    ADD COLUMN territory VARCHAR(200) NULL COMMENT '属地（省/市/区）' AFTER contact_info,
    ADD COLUMN customer_address VARCHAR(500) NULL COMMENT '客户地址' AFTER territory,
    ADD COLUMN xiyu_project_manager VARCHAR(100) NULL COMMENT '合同中西域项目负责人' AFTER customer_address;

-- 4. 新增附件资料相关字段
ALTER TABLE performance_record
    ADD COLUMN mall_website_url VARCHAR(500) NULL COMMENT '客户商城网站网址' AFTER xiyu_project_manager,
    ADD COLUMN has_bid_notice TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否有中标通知书: 0=否, 1=是' AFTER mall_website_url,
    ADD COLUMN remarks TEXT NULL COMMENT '备注' AFTER has_bid_notice;

-- 5. 把旧 project_name 的值同步到新 contract_name（数据迁移）
UPDATE performance_record SET contract_name = project_name WHERE contract_name IS NULL;

-- 6. 把旧 contract_date 的值同步到新 signing_date
UPDATE performance_record SET signing_date = contract_date WHERE signing_date IS NULL;

-- 7. 扩展附件类型枚举范围
-- performance_attachment.file_type 已是 VARCHAR(50)，无需改列，只在注释中说明新枚举值
ALTER TABLE performance_attachment
    MODIFY COLUMN file_type VARCHAR(50) COMMENT '附件类型: CONTRACT_AGREEMENT/MALL_SCREENSHOT/SOE_DIRECTORY/CATEGORY_PAGE/RELATIONSHIP_PROOF/BID_NOTICE/OTHER';

-- 8. 新增索引
ALTER TABLE performance_record
    ADD INDEX idx_perf_contract_name (contract_name),
    ADD INDEX idx_perf_customer_type (customer_type),
    ADD INDEX idx_perf_expiry (expiry_date),
    ADD INDEX idx_perf_signing_entity (signing_entity);
