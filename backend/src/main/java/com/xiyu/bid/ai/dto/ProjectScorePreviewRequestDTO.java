package com.xiyu.bid.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectScorePreviewRequestDTO {
    private Long projectId;
    private Long tenderId;
    private String projectName;
    private String industry;
    @NotNull
    private BigDecimal budget;
    private List<String> tags;
}
