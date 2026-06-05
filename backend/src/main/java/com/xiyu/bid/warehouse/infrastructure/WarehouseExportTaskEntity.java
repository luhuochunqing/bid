package com.xiyu.bid.warehouse.infrastructure;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "warehouse_export_task")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseExportTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExportStatus status;

    @Column(name = "filter_snapshot", columnDefinition = "TEXT")
    private String filterSnapshot;

    @Column(name = "total_count")
    private Integer totalCount;

    @Column(name = "stored_file_path", length = 500)
    private String storedFilePath;

    @Column(name = "download_url", length = 500)
    private String downloadUrl;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    public enum ExportStatus { PENDING, PROCESSING, COMPLETED, FAILED }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = ExportStatus.PENDING;
    }
}
