package com.xiyu.bid.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "template_use_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateUseRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @Column(name = "document_name", nullable = false)
    private String documentName;

    @Column(name = "doc_type", nullable = false)
    private String docType;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "applied_options")
    private String appliedOptions;

    @Column(name = "used_by")
    private Long usedBy;

    @Column(name = "used_at", nullable = false, updatable = false)
    private LocalDateTime usedAt;

    @PrePersist
    protected void onCreate() {
        usedAt = LocalDateTime.now();
    }
}
