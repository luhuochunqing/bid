-- V119: Add retired flag to business_qualifications for CO-157
-- Fixes retire/restore not persisting status

ALTER TABLE business_qualifications
    ADD COLUMN retired BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: any qualification with a non-empty retire_reason is retired
UPDATE business_qualifications
SET retired = TRUE
WHERE retire_reason IS NOT NULL AND retire_reason != '';
