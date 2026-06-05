package com.xiyu.bid.resources.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bar_site_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarSiteAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bar_asset_id", nullable = false)
    private Long barAssetId;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 30)
    private String role;

    @Column(nullable = false, length = 100)
    private String owner;

    @Column(nullable = false, length = 50)
    private String phone;

    @Column(length = 200)
    private String email;

    @Column(nullable = false, length = 30)
    private String status;

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
