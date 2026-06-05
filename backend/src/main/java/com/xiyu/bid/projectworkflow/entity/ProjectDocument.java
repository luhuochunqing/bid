package com.xiyu.bid.projectworkflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String name;

    @Column(name = "file_size")
    private String size;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "document_category")
    private String documentCategory;

    @Column(name = "linked_entity_type")
    private String linkedEntityType;

    @Column(name = "linked_entity_id")
    private Long linkedEntityId;

    @Column(name = "file_url", length = 1000)
    private String fileUrl;

    @Column(name = "uploader_id")
    private Long uploaderId;

    @Column(name = "uploader_name", nullable = false)
    private String uploaderName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
