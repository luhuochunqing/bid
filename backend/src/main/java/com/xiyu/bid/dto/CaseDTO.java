package com.xiyu.bid.dto;

import com.xiyu.bid.entity.Case;
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
    private Case.Industry industry;
    private Case.Outcome outcome;
    private BigDecimal amount;
    private LocalDate projectDate;
    private String description;
    private String customerName;
    private String locationName;
    private String projectPeriod;
    private List<String> tags;
    private List<String> highlights;
    private List<String> technologies;
    private Long viewCount;
    private Long useCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
