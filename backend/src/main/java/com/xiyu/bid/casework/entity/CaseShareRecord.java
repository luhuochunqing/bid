package com.xiyu.bid.casework.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "case_share_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseShareRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private String url;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_by_name", nullable = false)
    private String createdByName;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
