-- Input: V1144__qualification_agency_contact_comment.sql
-- Data rollback required: no data rollback needed; only column comment is changed.
-- CO-525 rollback: 恢复 agency_contact 列注释为原语义
ALTER TABLE business_qualifications
    MODIFY COLUMN agency_contact VARCHAR(200) COMMENT '代理联系方式';
