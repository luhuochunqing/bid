package com.xiyu.bid.bidresult.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bid_result_fetch_results", indexes = {
        @Index(name = "idx_bid_result_fetch_status", columnList = "status"),
        @Index(name = "idx_bid_result_fetch_project", columnList = "project_id"),
        @Index(name = "idx_bid_result_fetch_tender", columnList = "tender_id")
})
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BidResultFetchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String source;

    @Column(name = "tender_id")
    private Long tenderId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "project_name", nullable = false, length = 500)
    private String projectName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Result result;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "fetch_time", nullable = false)
    private LocalDateTime fetchTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "ignored_reason", length = 255)
    private String ignoredReason;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_type", length = 20)
    private RegistrationType registrationType;

    @Column(name = "contract_start_date")
    private LocalDate contractStartDate;

    @Column(name = "contract_end_date")
    private LocalDate contractEndDate;

    @Column(name = "contract_duration_months")
    private Integer contractDurationMonths;

    @Column(name = "remark", length = 2000)
    private String remark;

    @Column(name = "sku_count")
    private Integer skuCount;

    @Column(name = "win_announce_doc_url", length = 500)
    private String winAnnounceDocUrl;

    @Column(name = "notice_document_id")
    private Long noticeDocumentId;

    @Column(name = "analysis_document_id")
    private Long analysisDocumentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (fetchTime == null) {
            fetchTime = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Result {
        WON,
        LOST
    }

    public enum Status {
        PENDING,
        CONFIRMED,
        IGNORED
    }

    public enum RegistrationType {
        MANUAL,
        SYNC,
        FETCH
    }
}
