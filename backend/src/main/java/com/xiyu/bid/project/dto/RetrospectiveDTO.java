// Input: ProjectRetrospective 实体
// Output: 出参 DTO (PRD §3.3.1.5)
// Pos: project/dto/
package com.xiyu.bid.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrospectiveDTO {
    private Long id;
    private Long projectId;
    private String resultType;
    private String summary;
    private String winFactors;
    private String lossReasons;
    private String competitorNotes;
    private String improvementActions;
    private String processHighlights;
    // V1035 新增
    private LocalDateTime meetingTime;
    private String meetingFormat;
    private String meetingParticipants;
    private List<String> lossReasonFlags;
    private String postWinImprovements;
    private String processProblems;
    private String postLossMeasures;
    private List<Long> reportFileIds;
    private String reviewStatus;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
