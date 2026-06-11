-- U119: Rollback retired flag from business_qualifications

ALTER TABLE business_qualifications DROP COLUMN retired;
