ALTER TABLE tenders ADD COLUMN tender_agency VARCHAR(255);
ALTER TABLE tenders ADD COLUMN bid_opening_time DATETIME(6);
ALTER TABLE tenders ADD COLUMN customer_type VARCHAR(100);
ALTER TABLE tenders ADD COLUMN priority VARCHAR(10);

CREATE INDEX idx_tender_customer_type ON tenders (customer_type);
CREATE INDEX idx_tender_priority ON tenders (priority);
