// Input: 上传用户、共享存储路径和文件校验信息
// Output: 标书文件元数据聚合
// Pos: TenderUpload/Entity
// 维护声明: 仅维护上传文件元数据，不承载任务调度语义.
package com.xiyu.bid.tenderupload.entity;

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

@Entity
@Table(name = "tender_file", indexes = {
        @Index(name = "idx_tender_file_user_status_created", columnList = "user_id, upload_status, created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "upload_id", nullable = false, length = 64, unique = true)
    private String uploadId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "file_sha256", length = 64)
    private String fileSha256;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "page_count")
    private Integer pageCount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "upload_status", nullable = false, length = 20)
    @Builder.Default
    private TenderFileUploadStatus uploadStatus = TenderFileUploadStatus.INITIATED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
