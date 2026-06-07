package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderSearchCriteria {

    private String keyword;
    private List<Tender.Status> status;
    private List<String> source;
    private Tender.SourceType sourceType;
    private String region;
    private String industry;
    private String purchaserName;
    private String purchaserHash;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime deadlineFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime deadlineTo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate publishDateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate publishDateTo;

    private List<String> customerType;
    private List<String> priority;
    private String projectType;
    private Long projectManagerId;
    private Long biddingPersonId;
    private Long creatorId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime bidOpeningTimeFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime bidOpeningTimeTo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime registrationDeadlineFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime registrationDeadlineTo;

    private Integer aiScoreMin;
    private Integer aiScoreMax;

    /** 增量同步：按 updatedAt >= 过滤 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime updatedSince;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdAtFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdAtTo;

    /** 分页页码（0-based，默认 0） */
    @Builder.Default
    private int page = 0;

    /** 每页大小（默认 20，上限 200） */
    @Builder.Default
    private int size = 20;

    public static TenderSearchCriteria empty() {
        return new TenderSearchCriteria();
    }
}
