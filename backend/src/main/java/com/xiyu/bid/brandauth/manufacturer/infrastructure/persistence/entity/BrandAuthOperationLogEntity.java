package com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "brand_auth_operation_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrandAuthOperationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "authorization_id", nullable = false)
    private Long authorizationId;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_username", nullable = false, length = 100)
    private String operatorUsername;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "summary", length = 255)
    private String summary;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
