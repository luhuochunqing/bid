-- V1022__add_personnel_screening_indexes.sql
-- 为「筛选与搜索」h5 增加性能索引
-- 支持多维度筛选（尤其是持有证书、最高学历、证书状态等跨表查询）

-- 教育经历表索引（支持最高学历多选、学习形式多选、专业模糊）
CREATE INDEX idx_edu_personnel_highest ON personnel_education (personnel_id, highest_education);
CREATE INDEX idx_edu_personnel_study_form ON personnel_education (personnel_id, study_form);
CREATE INDEX idx_edu_personnel_major ON personnel_education (personnel_id, major);

-- 证书表索引（支持证书名称模糊搜索 + 到期日期判断）
CREATE INDEX idx_cert_personnel_name ON personnel_certificate (personnel_id, certificate_name);
CREATE INDEX idx_cert_personnel_expiry ON personnel_certificate (personnel_id, expiry_date);

-- 人员表索引（支持性别 + 入职时间范围常见组合）
CREATE INDEX idx_personnel_gender_entry ON personnel (gender, entry_date);
