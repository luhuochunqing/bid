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
@Table(name = "document_archive_records", indexes = {
        @Index(name = "idx_document_archive_project", columnList = "project_id"),
        @Index(name = "idx_document_archive_structure", columnList = "structure_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentArchiveRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "structure_id", nullable = false)
    private Long structureId;

    @Column(name = "archived_by")
    private Long archivedBy;

    @Column(name = "archived_by_name", nullable = false)
    private String archivedByName;

    @Column(name = "archive_reason", nullable = false)
    private String archiveReason;

    @Column(name = "export_id")
    private Long exportId;

    @Column(name = "archived_at", nullable = false)
    private LocalDateTime archivedAt;

    @PrePersist
    protected void onCreate() {
        archivedAt = LocalDateTime.now();
    }
}
