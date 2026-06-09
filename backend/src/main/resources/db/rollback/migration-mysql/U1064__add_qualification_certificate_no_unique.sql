-- U1064__add_qualification_certificate_no_unique.sql
-- Input: V1064__add_qualification_certificate_no_unique.sql
-- Rollback for V1064__add_qualification_certificate_no_unique.sql
-- PR #336: 回滚 certificate_no 唯一约束
ALTER TABLE business_qualifications
DROP INDEX IF EXISTS uk_certificate_no;
