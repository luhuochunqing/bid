package com.xiyu.bid.projectquality.entity;

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
@Table(name = "project_quality_checks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectQualityCheck {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "document_name")
    private String documentName;

    @Column(nullable = false)
    private String status;

    @Column(name = "`empty`", nullable = false)
    private boolean empty;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @PrePersist
    void onCreate() {
        if (checkedAt == null) {
            checkedAt = LocalDateTime.now();
        }
    }
}
