-- Input: V1023__add_personnel_screening_indexes.sql
-- Rollback for V1023__add_personnel_screening_indexes.sql

DROP INDEX idx_personnel_gender_entry ON personnel;
DROP INDEX idx_cert_personnel_expiry ON personnel_certificate;
DROP INDEX idx_cert_personnel_name ON personnel_certificate;
DROP INDEX idx_edu_personnel_major ON personnel_education;
DROP INDEX idx_edu_personnel_study_form ON personnel_education;
DROP INDEX idx_edu_personnel_highest ON personnel_education;
