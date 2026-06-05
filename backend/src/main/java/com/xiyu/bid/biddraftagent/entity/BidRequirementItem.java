package com.xiyu.bid.biddraftagent.entity;

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
@Table(name = "bid_requirement_items", indexes = {
        @Index(name = "idx_bid_requirement_items_project", columnList = "project_id"),
        @Index(name = "idx_bid_requirement_items_tender", columnList = "tender_id"),
        @Index(name = "idx_bid_requirement_items_document", columnList = "project_document_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidRequirementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "tender_id", nullable = false)
    private Long tenderId;

    @Column(name = "project_document_id", nullable = false)
    private Long projectDocumentId;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "source_excerpt", columnDefinition = "text")
    private String sourceExcerpt;

    @Column(nullable = false)
    private boolean mandatory;

    @Column
    private Integer confidence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
