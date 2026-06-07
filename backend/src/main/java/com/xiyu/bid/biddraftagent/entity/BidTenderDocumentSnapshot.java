package com.xiyu.bid.biddraftagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bid_tender_document_snapshots", indexes = {
        @Index(name = "idx_bid_tender_doc_snap_project", columnList = "project_id"),
        @Index(name = "idx_bid_tender_doc_snap_tender", columnList = "tender_id"),
        @Index(name = "idx_bid_tender_doc_snap_document", columnList = "project_document_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidTenderDocumentSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "tender_id", nullable = false)
    private Long tenderId;

    @Column(name = "project_document_id", nullable = false)
    private Long projectDocumentId;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "file_url", length = 1000)
    private String fileUrl;

    @Column(name = "storage_path", length = 1000)
    private String storagePath;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Column(name = "extracted_text", nullable = false, columnDefinition = "text")
    private String extractedText;

    @Column(name = "profile_json", nullable = false, columnDefinition = "text")
    private String profileJson;

    @Column(name = "extractor_key", nullable = false, length = 100)
    private String extractorKey;

    @Column(name = "analyzer_key", nullable = false, length = 100)
    private String analyzerKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
