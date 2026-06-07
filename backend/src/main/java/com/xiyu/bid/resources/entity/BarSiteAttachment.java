package com.xiyu.bid.resources.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bar_site_attachments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarSiteAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bar_asset_id", nullable = false)
    private Long barAssetId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 50)
    private String size;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(length = 500)
    private String url;

    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
    }
}
