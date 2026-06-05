package com.xiyu.bid.resources.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bar_site_sops")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarSiteSop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bar_asset_id", nullable = false, unique = true)
    private Long barAssetId;

    @Column(name = "reset_url", length = 500)
    private String resetUrl;

    @Column(name = "unlock_url", length = 500)
    private String unlockUrl;

    @Column(length = 100)
    private String estimatedTime;

    @Column(name = "contacts_json", columnDefinition = "TEXT")
    private String contactsJson;

    @Column(name = "required_docs_json", columnDefinition = "TEXT")
    private String requiredDocsJson;

    @Column(name = "faqs_json", columnDefinition = "TEXT")
    private String faqsJson;

    @Column(name = "history_json", columnDefinition = "TEXT")
    private String historyJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
