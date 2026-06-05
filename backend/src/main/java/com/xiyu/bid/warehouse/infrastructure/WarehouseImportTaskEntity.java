package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.warehouse.domain.ImportTaskStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "warehouse_import_task")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseImportTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportTaskStatus status;

    @Column(name = "total_rows")
    private Integer totalRows;

    @Column(name = "valid_rows")
    private Integer validRows;

    @Column(name = "invalid_rows")
    private Integer invalidRows;

    @Column(name = "imported_rows")
    private Integer importedRows;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    @Column(name = "source_file_path", length = 500)
    private String sourceFilePath;

    @Column(name = "source_filename", length = 255)
    private String sourceFilename;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_by_username", length = 100)
    private String createdByUsername;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = ImportTaskStatus.PENDING;
    }
}
