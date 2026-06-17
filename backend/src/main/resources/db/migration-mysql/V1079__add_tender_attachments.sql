CREATE TABLE tender_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tender_id BIGINT NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_type VARCHAR(100),
    file_url VARCHAR(2000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tender_attachment_tender FOREIGN KEY (tender_id) REFERENCES tenders(id) ON DELETE CASCADE
);
CREATE INDEX idx_tender_attachment_tender_id ON tender_attachments(tender_id);
