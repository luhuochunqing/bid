package com.xiyu.bid.documentexport.entity;

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
@Table(name = "document_exports", indexes = {
        @Index(name = "idx_document_export_project", columnList = "project_id"),
        @Index(name = "idx_document_export_structure", columnList = "structure_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentExport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "structure_id", nullable = false)
    private Long structureId;

    @Column(name = "project_name", nullable = false)
    private String projectName;

    @Column(nullable = false)
    private String format;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "exported_by")
    private Long exportedBy;

    @Column(name = "exported_by_name", nullable = false)
    private String exportedByName;

    @Column(name = "exported_at", nullable = false)
    private LocalDateTime exportedAt;

    @PrePersist
    protected void onCreate() {
        exportedAt = LocalDateTime.now();
    }
}
