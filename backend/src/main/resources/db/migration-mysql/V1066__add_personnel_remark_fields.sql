-- Add remark to personnel and personnel_certificate
ALTER TABLE personnel ADD COLUMN remark VARCHAR(500);

ALTER TABLE personnel_certificate ADD COLUMN remark VARCHAR(500);