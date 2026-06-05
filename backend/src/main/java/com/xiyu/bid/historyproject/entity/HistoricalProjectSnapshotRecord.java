package com.xiyu.bid.historyproject.entity;

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
@Table(name = "historical_project_snapshots", indexes = {
        @Index(name = "idx_history_snapshot_project", columnList = "project_id"),
        @Index(name = "idx_history_snapshot_archive", columnList = "archive_record_id"),
        @Index(name = "idx_history_snapshot_export", columnList = "export_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalProjectSnapshotRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "archive_record_id", nullable = false, unique = true)
    private Long archiveRecordId;

    @Column(name = "export_id", nullable = false)
    private Long exportId;

    @Column(name = "project_name", nullable = false, length = 500)
    private String projectName;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(name = "product_line", length = 255)
    private String productLine;

    @Column(name = "archive_summary", columnDefinition = "TEXT", nullable = false)
    private String archiveSummary;

    @Column(name = "document_snapshot_text", columnDefinition = "TEXT", nullable = false)
    private String documentSnapshotText;

    @Column(name = "recommended_tags", columnDefinition = "TEXT")
    private String recommendedTags;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private LocalDateTime capturedAt;

    @PrePersist
    protected void onCreate() {
        capturedAt = LocalDateTime.now();
    }
}
