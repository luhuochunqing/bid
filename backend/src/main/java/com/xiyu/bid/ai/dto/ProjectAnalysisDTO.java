package com.xiyu.bid.ai.dto;

import com.xiyu.bid.entity.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Project Analysis DTO
 * Contains project information along with AI analysis results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectAnalysisDTO {

    /**
     * Project ID
     */
    private Long projectId;

    /**
     * Project name
     */
    private String projectName;

    /**
     * Project status
     */
    private Project.Status status;

    /**
     * Associated tender ID
     */
    private Long tenderId;

    /**
     * Manager ID
     */
    private Long managerId;

    /**
     * Team member IDs
     */
    private List<Long> teamMembers;

    /**
     * Start date
     */
    private LocalDateTime startDate;

    /**
     * End date
     */
    private LocalDateTime endDate;

    /**
     * AI analysis results
     */
    private AiAnalysisResponse analysis;

    /**
     * Analysis timestamp
     */
    private LocalDateTime analyzedAt;
}
