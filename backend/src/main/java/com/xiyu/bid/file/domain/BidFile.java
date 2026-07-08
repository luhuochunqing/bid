package com.xiyu.bid.file.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 大文件上传元数据聚合根。
 *
 * <p>真实文件数据流存储在华为云 OBS，本实体仅维护上传状态与元数据。</p>
 */
@Entity
@Table(
        name = "bid_file",
        indexes = {
                @Index(name = "idx_bid_file_status", columnList = "status"),
                @Index(name = "idx_bid_file_creator", columnList = "creator_id"),
                @Index(name = "idx_bid_file_created_at", columnList = "created_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "upload_id", nullable = false, length = 64, unique = true)
    private String uploadId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "varchar(20)")
    @Builder.Default
    private BidFileStatus status = BidFileStatus.UPLOADING;

    @Column(name = "original_name", nullable = false, length = 500)
    private String originalName;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "bucket", nullable = false, length = 100)
    private String bucket;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void transitionTo(BidFileStatus nextStatus) {
        this.status = nextStatus;
        if (nextStatus == BidFileStatus.COMPLETED) {
            this.completedAt = LocalDateTime.now();
        }
    }

    public void fail(String message) {
        this.status = BidFileStatus.FAILED;
        this.errorMessage = message;
    }
}
