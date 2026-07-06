-- CO-525 fix: 同步 agency_contact 列注释与业务语义（代理机构联系人）
ALTER TABLE business_qualifications
    MODIFY COLUMN agency_contact VARCHAR(200) COMMENT '代理机构联系人';
