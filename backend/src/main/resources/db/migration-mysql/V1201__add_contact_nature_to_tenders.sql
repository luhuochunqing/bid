-- V1201: 标讯联系人性质字段（contactNature / contactNature2）
--
-- 背景：PR !2349 联系人性质全链路——标讯手动创建/编辑路径区分
-- 联系人1/联系人2 的性质（客户/代理/标前），CRM 集成路径豁免。
-- 版本号经历 V1189→V1198→V1200→V1201 连续避让 origin/main 占用。
--
-- VARCHAR(20) NULL：性质为可选枚举文案，允许为空，不设 DEFAULT。

ALTER TABLE tenders ADD COLUMN contact_nature VARCHAR(20) NULL COMMENT '联系人1性质(客户/代理/标前)';
ALTER TABLE tenders ADD COLUMN contact_nature2 VARCHAR(20) NULL COMMENT '联系人2性质(客户/代理/标前)';
