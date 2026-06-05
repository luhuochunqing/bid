package com.xiyu.bid.casework.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "case_reference_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseReferenceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "referenced_by")
    private Long referencedBy;

    @Column(name = "referenced_by_name", nullable = false)
    private String referencedByName;

    @Column(name = "reference_target", nullable = false)
    private String referenceTarget;

    @Column(name = "reference_context")
    private String referenceContext;

    @Column(name = "source_project_name")
    private String sourceProjectName;

    @Column(name = "referenced_at", nullable = false, updatable = false)
    private LocalDateTime referencedAt;

    @PrePersist
    protected void onCreate() {
        referencedAt = LocalDateTime.now();
    }
}
