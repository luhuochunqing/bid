-- V1135: Create bid_case_slice table for AI case slice semantic search
-- AI 案例切片语义检索 — 存储历史投标文件章节切片及 embedding 向量
CREATE TABLE bid_case_slice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_dir VARCHAR(200) NOT NULL,
    project_idx INT NOT NULL,
    docx_file VARCHAR(500) NOT NULL,
    docx_label VARCHAR(20) NOT NULL,
    section_idx INT NOT NULL,
    level INT NOT NULL,
    title VARCHAR(500) NOT NULL,
    text_preview TEXT NOT NULL,
    text_length INT NOT NULL,
    para_count INT NOT NULL,
    embedding MEDIUMBLOB,
    embedding_model VARCHAR(100),
    embedding_dim INT,
    embedding_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_bid_case_slice_project (project_dir),
    INDEX idx_bid_case_slice_label (docx_label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
