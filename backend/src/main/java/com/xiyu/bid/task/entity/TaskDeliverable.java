package com.xiyu.bid.task.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * Task deliverable — files/documents associated with a task.
 * Follows ProjectDocument pattern with task-scoping,
 * versioning, and type taxonomy.
 */
@Entity
@Table(name = "task_deliverables", indexes = {
        @Index(name = "idx_task_del_task_id",
                columnList = "task_id"),
        @Index(name = "idx_task_del_type",
                columnList = "deliverable_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDeliverable {

    /** Column length for file size display string. */
    private static final int LEN_SIZE = 50;
    /** Column length for MIME type. */
    private static final int LEN_FILE_TYPE = 100;
    /** Column length for storage path. */
    private static final int LEN_STORAGE_PATH = 500;
    /** Column length for storage key. */
    private static final int LEN_STORAGE_KEY = 255;
    /** Column length for uploader name. */
    private static final int LEN_UPLOADER_NAME = 100;

    /** Unique identifier. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Foreign key to owning task. */
    @Column(name = "task_id", nullable = false)
    private Long taskId;

    /** Deliverable display name. */
    @Column(nullable = false)
    private String name;

    /** Deliverable type classification. */
    @Enumerated(EnumType.STRING)
    @Column(name = "deliverable_type", nullable = false)
    private DeliverableType deliverableType;

    /** File size display string. */
    @Column(name = "file_size", length = LEN_SIZE)
    private String size;

    /** MIME type of uploaded file. */
    @Column(name = "file_type", length = LEN_FILE_TYPE)
    private String fileType;

    /** Storage path (v1 may be null). */
    @Column(name = "storage_path", length = LEN_STORAGE_PATH)
    private String storagePath;

    /** Unique storage key for retrieval. */
    @Column(name = "storage_key",
            length = LEN_STORAGE_KEY, unique = true)
    private String storageKey;

    /** Version number starting from 1. */
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    /** Uploader user id. */
    @Column(name = "uploader_id")
    private Long uploaderId;

    /** Uploader display name (denormalized). */
    @Column(name = "uploader_name",
            nullable = false, length = LEN_UPLOADER_NAME)
    private String uploaderName;

    /** Creation timestamp. */
    @Column(name = "created_at",
            nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Auto-set creation timestamp before persist. */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Deliverable type matching frontend dropdown options exactly.
     */
    public enum DeliverableType {
        /** General document. */
        DOCUMENT,
        /** Qualification certificate. */
        QUALIFICATION,
        /** Technical proposal. */
        TECHNICAL,
        /** Price quotation. */
        QUOTATION,
        /** Other/miscellaneous. */
        OTHER
    }
}
