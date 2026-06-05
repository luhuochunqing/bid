package com.xiyu.bid.casework.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 案例数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseDTO {

    private Long id;
    private String title;
    private Industry industry;
    private Outcome outcome;
    private BigDecimal amount;
    private LocalDate projectDate;
    private String description;
    private String customerName;
    private String locationName;
    private String projectPeriod;
    private String productLine;
    private Long sourceProjectId;
    private String archiveSummary;
    private String priceStrategy;
    private List<String> successFactors;
    private List<String> lessonsLearned;
    private String documentSnapshotText;
    private List<String> attachmentNames;
    private String status;
    private LocalDateTime publishedAt;
    private String visibility;
    private String searchDocument;
    private List<String> tags;
    private List<String> highlights;
    private List<String> technologies;
    private Long viewCount;
    private Long useCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum Industry {
        REAL_ESTATE,
        INFRASTRUCTURE,
        MANUFACTURING,
        ENERGY,
        TRANSPORTATION,
        ENVIRONMENTAL,
        OTHER
    }

    public enum Outcome {
        WON,
        LOST,
        IN_PROGRESS
    }
}
