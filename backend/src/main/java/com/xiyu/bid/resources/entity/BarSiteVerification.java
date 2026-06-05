package com.xiyu.bid.resources.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bar_site_verifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarSiteVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bar_asset_id", nullable = false)
    private Long barAssetId;

    @Column(name = "verified_by", nullable = false, length = 100)
    private String verifiedBy;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(length = 500)
    private String message;

    @PrePersist
    protected void onCreate() {
        if (verifiedAt == null) {
            verifiedAt = LocalDateTime.now();
        }
    }
}
