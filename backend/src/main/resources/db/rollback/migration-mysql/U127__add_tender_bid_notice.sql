-- Input: migration-mysql/V127__add_tender_bid_notice.sql
-- Output: rollback script for mysql environments

ALTER TABLE tenders
    DROP COLUMN bid_notice,
    DROP COLUMN bid_notice_file_url;
