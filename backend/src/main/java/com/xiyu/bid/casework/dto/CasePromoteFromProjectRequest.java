package com.xiyu.bid.casework.dto;

import jakarta.validation.constraints.NotNull;
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
public class CasePromoteFromProjectRequest {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    private String title;
    private CaseDTO.Industry industry;
    private CaseDTO.Outcome outcome;
    private BigDecimal amount;
    private LocalDate projectDate;
    private String description;
    private String customerName;
    private String locationName;
    private String projectPeriod;
    private List<String> tags;
    private List<String> highlights;
    private List<String> technologies;
    private String productLine;
    private String archiveSummary;
    private String priceStrategy;
    private List<String> successFactors;
    private List<String> lessonsLearned;
    private String documentSnapshotText;
    private List<String> attachmentNames;
    private String status;
    private LocalDateTime publishedAt;
    private String visibility;
}
