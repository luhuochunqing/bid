package com.xiyu.bid.entity;

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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 标讯实体
 * 管理招标信息的基础数据
 */
@Entity
@Table(name = "tenders", indexes = {
    @Index(name = "idx_tender_status", columnList = "status"),
    @Index(name = "idx_tender_source", columnList = "source"),
    @Index(name = "idx_tender_deadline", columnList = "deadline"),
    @Index(name = "idx_tender_ai_score", columnList = "ai_score"),
    @Index(name = "idx_tender_external_id", columnList = "external_id", unique = true),
    @Index(name = "idx_tender_region", columnList = "region"),
    @Index(name = "idx_tender_industry", columnList = "industry"),
    @Index(name = "idx_tender_purchaser_hash", columnList = "purchaser_hash"),
    @Index(name = "idx_tender_status_region_industry", columnList = "status, region, industry"),
    @Index(name = "idx_tender_source_normalized", columnList = "source_normalized"),
    @Index(name = "idx_tender_region_normalized", columnList = "region_normalized"),
    @Index(name = "idx_tender_industry_normalized", columnList = "industry_normalized"),
    @Index(name = "idx_tender_purchaser_hash_normalized", columnList = "purchaser_hash_normalized"),
    @Index(name = "idx_tender_customer_type", columnList = "customer_type"),
    @Index(name = "idx_tender_priority", columnList = "priority"),
    @Index(name = "idx_tender_registration_deadline", columnList = "registration_deadline"),
    @Index(name = "idx_tender_bid_opening_time", columnList = "bid_opening_time"),
    @Index(name = "idx_tender_status_region_industry_normalized",
            columnList = "status, region_normalized, industry_normalized"),
    @Index(name = "idx_tender_source_type", columnList = "source_type")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Tender {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 外部平台唯一标识
     */
    @Column(name = "external_id", length = 100, unique = true)
    private String externalId;

    /**
     * 标题
     */
    @Column(nullable = false, length = 500)
    private String title;

    /**
     * 来源
     */
    @Column(length = 200)
    private String source;

    /**
     * 原始链接
     */
    @Column(name = "original_url", length = 1000)
    private String originalUrl;

    @Column(precision = 19, scale = 2)
    private BigDecimal budget;

    @Column(length = 100)
    private String region;

    @Column(length = 100)
    private String industry;

    @Column(name = "tender_agency", length = 255)
    private String tenderAgency;

    @Column(name = "purchaser_name", length = 255)
    private String purchaserName;

    @Column(name = "purchaser_hash", length = 64)
    private String purchaserHash;
    @Column(name = "source_normalized", length = 200)
    private String sourceNormalized;

    @Column(name = "region_normalized", length = 100)
    private String regionNormalized;

    @Column(name = "industry_normalized", length = 100)
    private String industryNormalized;

    @Column(name = "purchaser_hash_normalized", length = 64)
    private String purchaserHashNormalized;

    @Column(name = "purchaser_name_normalized", length = 255)
    private String purchaserNameNormalized;

    @Column(name = "search_text_normalized", columnDefinition = "text")
    private String searchTextNormalized;

    @Column(name = "publish_date")
    private LocalDate publishDate;

    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Column(name = "bid_opening_time")
    private LocalDateTime bidOpeningTime;

    @Column(name = "registration_deadline", nullable = true)
    private LocalDateTime registrationDeadline;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;


    /* 联系人1座机 */
    @Column(name = "contact_tel", length = 50)
    private String contactTel;

    /* 联系人1邮箱 */
    @Column(name = "contact_mail", length = 100)
    private String contactMail;

    /* 联系人2 */
    @Column(name = "contact_name2", length = 100)
    private String contactName2;

    @Column(name = "contact_phone2", length = 50)
    private String contactPhone2;

    @Column(name = "contact_tel2", length = 50)
    private String contactTel2;

    @Column(name = "contact_mail2", length = 100)
    private String contactMail2;

    /* 项目类型 */
    @Column(name = "project_type", length = 20)
    private String projectType;

    /* 标讯信息 */
    @Column(name = "tender_info", length = 5000)
    private String tenderInfo;

    /* 来源平台 */
    @Column(name = "source_platform", length = 100)
    private String sourcePlatform;

    /* CRM商机ID（字符串，如 "CRM-OPP-001"） */
    @Column(name = "crm_opportunity_id", length = 64)
    private String crmOpportunityId;

    /* 人员分配 */
    @Column(name = "project_manager_id")
    private Long projectManagerId;

    @Column(name = "project_manager_name", length = 100)
    private String projectManagerName;

    @Column(name = "bidding_person_id")
    private Long biddingPersonId;

    @Column(name = "bidding_person_name", length = 100)
    private String biddingPersonName;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "distributor_id")
    private Long distributorId;

    @Column(name = "distributor_name", length = 100)
    private String distributorName;

    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "creator_name", length = 100)
    private String creatorName;

    /* 标讯信息 */
    @Column(name = "bid_notice", columnDefinition = "text")
    private String bidNotice;

    @Column(name = "bid_notice_file_url", length = 1000)
    private String bidNoticeFileUrl;

    @Column(name = "source_document_name", length = 255)
    private String sourceDocumentName;

    @Column(name = "source_document_file_type", length = 100)
    private String sourceDocumentFileType;

    @Column(name = "source_document_file_url", length = 1000)
    private String sourceDocumentFileUrl;

    @Column(name = "customer_type", length = 100)
    private String customerType;

    @Column(name = "priority", length = 10)
    private String priority;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Tender.Status status = Tender.Status.PENDING_ASSIGNMENT;

    @Column(name = "abandonment_reason", length = 1000)
    private String abandonmentReason;

    @Column(name = "ai_score")
    private Integer aiScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private Tender.RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 20)
    @Builder.Default
    private Tender.SourceType sourceType = Tender.SourceType.MANUAL_SINGLE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 基本信息最近一次保存时间。
     * - NULL：标讯刚创建，尚未填写/保存基本信息
     * - NOT NULL：已保存基本信息（可能是人工录入时保存，也可能是后续编辑保存）
     * 用于前端区分"录入阶段一（未保存）"与"录入阶段二（已保存待分配）"的按钮展示。
     */
    @Column(name = "basic_info_saved_at")
    private LocalDateTime basicInfoSavedAt;

    /** 关联的CRM商机名称（项目负责人触发商机关联后写入）。 */
    @Column(name = "crm_opportunity_name", length = 200)
    private String crmOpportunityName;

    /** 关联的投标项目ID（投标立项后写入，项目状态变更时回填标讯状态）。 */
    @Column(name = "project_id")
    private Long projectId;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        refreshSearchColumns();
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        refreshSearchColumns();
        updatedAt = LocalDateTime.now();
    }

    private void refreshSearchColumns() {
        sourceNormalized = normalize(source);
        regionNormalized = normalize(region);
        industryNormalized = normalize(industry);
        purchaserHashNormalized = normalize(purchaserHash);
        purchaserNameNormalized = normalize(purchaserName);
        searchTextNormalized = normalizeSearchText();
    }

    private String normalizeSearchText() {
        return normalize(String.join(" ",
                nullToBlank(title),
                nullToBlank(description),
                nullToBlank(purchaserName),
                nullToBlank(tenderAgency),
                nullToBlank(customerType),
                nullToBlank(priority),
                nullToBlank(tags),
                nullToBlank(region),
                nullToBlank(industry),
                nullToBlank(source),
                nullToBlank(contactName),
                nullToBlank(contactName2),
                nullToBlank(projectManagerName),
                nullToBlank(biddingPersonName),
                nullToBlank(bidNotice)
        ));
    }

    private static String normalize(String value) {
        return nullToBlank(value).trim().toLowerCase(Locale.ROOT);
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    /**
     * 标讯状态枚举
     */
    public enum Status {
        PENDING_ASSIGNMENT, // 待分配
        TRACKING,           // 跟踪中
        EVALUATED,          // 已评估
        BIDDING,            // 投标中
        WON,                // 已中标
        LOST,               // 未中标
        ABANDONED           // 已放弃
    }

    /**
     * 风险等级枚举
     */
    public enum RiskLevel {
        LOW,      // 低风险
        MEDIUM,   // 中风险
        HIGH      // 高风险
    }

    /**
     * 标讯来源类型枚举
     */
    public enum SourceType {
        EXTERNAL_PLATFORM,  // 第三方平台拉取
        CRM_OPPORTUNITY,    // CRM商机转入
        MANUAL_SINGLE,      // 人工单条录入
        BULK_IMPORT         // 批量Excel导入
    }
}
