package com.xiyu.bid.tender.dto;

import com.xiyu.bid.entity.Tender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderDTO {

    private Long id;
    private String title;
    private String source;
    private BigDecimal budget;
    private String region;
    private String industry;
    private String tenderAgency;
    private String purchaserName;
    private String purchaserHash;
    private LocalDate publishDate;
    private LocalDateTime deadline;
    private LocalDateTime bidOpeningTime;
    private LocalDateTime registrationDeadline;
    private String contactName;
    private String contactPhone;
    private String contactTel;
    private String contactMail;
    private String contactName2;
    private String contactPhone2;
    private String contactTel2;
    private String contactMail2;
    private String sourceDocumentName;
    private String sourceDocumentFileType;
    private String sourceDocumentFileUrl;
    private String customerType;
    private String priority;
    private String description;
    private List<String> tags;
    private Tender.Status status;
    private Integer aiScore;
    private Tender.RiskLevel riskLevel;
    private Tender.SourceType sourceType;
    private String originalUrl;
    private String externalId;
    private Long projectManagerId;
    private String projectManagerName;
    private Long biddingPersonId;
    private String biddingPersonName;
    private String department;
    private Long distributorId;
    private String distributorName;
    private Long creatorId;
    private String creatorName;
    private String bidNotice;
    private String bidNoticeFileUrl;
    private String projectType;
    private Long projectId;
    private String abandonmentReason;
    private String tenderInfo;
    private String sourcePlatform;
    private String crmOpportunityId;
    private String crmOpportunityName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 基本信息最近一次保存时间（见 Tender.basicInfoSavedAt） */
    private LocalDateTime basicInfoSavedAt;

    /** 分配人名称 */
    private String assigneeName;
}
