package com.xiyu.bid.bidresult.entity;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "competitor_win_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorWinRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "competitor_id", nullable = false)
    private Long competitorId;

    @Column(name = "competitor_name", nullable = false, length = 200)
    private String competitorName;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "project_name", length = 500)
    private String projectName;

    @Column(name = "sku_count")
    private Integer skuCount;

    @Column(length = 200)
    private String category;

    @Column(length = 100)
    private String discount;

    @Column(name = "payment_terms", length = 200)
    private String paymentTerms;

    @Column(name = "won_at")
    private LocalDate wonAt;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 2000)
    private String notes;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(name = "recorded_name", length = 100)
    private String recordedByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
