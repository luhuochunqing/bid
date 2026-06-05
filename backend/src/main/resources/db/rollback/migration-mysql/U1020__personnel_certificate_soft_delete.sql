-- Input: V1020__personnel_certificate_soft_delete.sql
-- Rollback for V1020__personnel_certificate_soft_delete.sql

DROP INDEX idx_cert_personnel_active ON personnel_certificate;

ALTER TABLE personnel_certificate
    DROP COLUMN deleted_at;
