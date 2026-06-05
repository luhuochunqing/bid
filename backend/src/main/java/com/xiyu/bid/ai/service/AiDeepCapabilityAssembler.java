package com.xiyu.bid.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.ai.core.AiJobLifecyclePolicy;
import com.xiyu.bid.ai.core.ProjectScorePreviewPolicy;
import com.xiyu.bid.ai.core.TenderAiAnalysisPolicy;
import com.xiyu.bid.ai.dto.AiAutoTaskDTO;
import com.xiyu.bid.ai.dto.AiRiskItemDTO;
import com.xiyu.bid.ai.dto.AiSummaryRiskDTO;
import com.xiyu.bid.ai.dto.AiSummaryViewDTO;
import com.xiyu.bid.ai.dto.DimensionScoreViewDTO;
import com.xiyu.bid.ai.dto.GapItemDTO;
import com.xiyu.bid.ai.dto.GeneratedTaskDTO;
import com.xiyu.bid.ai.dto.ProjectScorePreviewDTO;
import com.xiyu.bid.ai.dto.ProjectScorePreviewRequestDTO;
import com.xiyu.bid.ai.dto.ScoreAnalysisPreviewDTO;
import com.xiyu.bid.ai.dto.ScoreCategoryCoverageDTO;
import com.xiyu.bid.ai.dto.TenderAiAnalysisDTO;
import com.xiyu.bid.ai.entity.AiAnalysisJob;
import com.xiyu.bid.ai.entity.AiAnalysisResult;
import com.xiyu.bid.ai.entity.ProjectScorePreview;
import com.xiyu.bid.entity.Tender;

import java.time.LocalDate;
import java.util.List;

final class AiDeepCapabilityAssembler {

    private AiDeepCapabilityAssembler() {
    }

    static TenderAiAnalysisPolicy.AnalysisInput toTenderAnalysisInput(com.xiyu.bid.ai.dto.AiAnalysisResponse response, LocalDate analysisDate) {
        return new TenderAiAnalysisPolicy.AnalysisInput(
            response == null ? null : response.getScore(),
            toCoreRiskLevel(response == null ? null : response.getRiskLevel()),
            response == null || response.getDimensionScores() == null ? List.of() : response.getDimensionScores().stream()
                .map(item -> new TenderAiAnalysisPolicy.DimensionRating(item.getDimension(), item.getScore()))
                .toList(),
            response == null || response.getWeaknesses() == null ? List.of() : response.getWeaknesses(),
            response == null || response.getRecommendations() == null ? List.of() : response.getRecommendations(),
            analysisDate
        );
    }

    static TenderAiAnalysisDTO toTenderAnalysisDto(Long tenderId, TenderAiAnalysisPolicy.AnalysisResult result) {
        return TenderAiAnalysisDTO.builder()
            .tenderId(tenderId)
            .winScore(result.winScore())
            .suggestion(result.suggestion())
            .dimensionScores(result.dimensionScores().stream()
                .map(item -> DimensionScoreViewDTO.builder()
                    .name(item.dimension())
                    .score(item.score())
                    .build())
                .toList())
            .risks(result.risks().stream()
                .map(item -> AiRiskItemDTO.builder()
                    .level(item.level())
                    .desc(item.desc())
                    .action(item.action())
                    .build())
                .toList())
            .autoTasks(result.autoTasks().stream()
                .map(item -> AiAutoTaskDTO.builder()
                    .id(item.id())
                    .title(item.title())
                    .owner(item.owner())
                    .dueDate(item.dueDate())
                    .priority(item.priority())
                    .build())
                .toList())
            .build();
    }

    static AiAnalysisResult toTenderAnalysisResult(Long jobId, Long tenderId, TenderAiAnalysisPolicy.AnalysisResult result, Tender.RiskLevel riskLevel, String payloadJson) {
        return AiAnalysisResult.builder()
            .jobId(jobId)
            .tenderId(tenderId)
            .analysisType(AiAnalysisJob.AnalysisType.TENDER_ANALYSIS)
            .score(result.winScore())
            .riskLevel(riskLevel == null ? "MEDIUM" : riskLevel.name())
            .suggestion(result.suggestion())
            .payloadJson(payloadJson)
            .build();
    }

    static ProjectScorePreviewPolicy.PreviewInput toProjectScorePreviewInput(ProjectScorePreviewRequestDTO request) {
        return new ProjectScorePreviewPolicy.PreviewInput(
            request.getProjectName(),
            request.getIndustry(),
            request.getBudget(),
            request.getTags() == null ? List.of() : request.getTags(),
            request.getProjectId(),
            request.getTenderId()
        );
    }

    static ProjectScorePreviewDTO toProjectScorePreviewDto(Long projectId, Long tenderId, ProjectScorePreviewPolicy.PreviewResult result) {
        return ProjectScorePreviewDTO.builder()
            .projectId(projectId)
            .tenderId(tenderId)
            .aiSummary(AiSummaryViewDTO.builder()
                .winScore(result.winScore())
                .winLevel(result.winLevel())
                .risks(result.risks().stream()
                    .map(item -> AiSummaryRiskDTO.builder()
                        .level(item.level())
                        .content(item.content())
                        .build())
                    .toList())
                .suggestions(result.suggestions())
                .build())
            .scoreAnalysis(ScoreAnalysisPreviewDTO.builder()
                .scoreCategories(result.scoreCategories().stream()
                    .map(item -> ScoreCategoryCoverageDTO.builder()
                        .name(item.name())
                        .weight(item.weight())
                        .covered(item.covered())
                        .total(item.total())
                        .percentage(item.percentage())
                        .gaps(item.gaps())
                        .build())
                    .toList())
                .gapItems(result.gapItems().stream()
                    .map(item -> GapItemDTO.builder()
                        .category(item.category())
                        .scorePoint(item.scorePoint())
                        .required(item.required())
                        .status(item.status())
                        .build())
                    .toList())
                .build())
            .generatedTasks(result.generatedTasks().stream()
                .map(item -> GeneratedTaskDTO.builder()
                    .name(item.name())
                    .priority(item.priority())
                    .suggestion(item.suggestion())
                    .selected(item.selected())
                    .build())
                .toList())
            .build();
    }

    static ProjectScorePreview toProjectScorePreviewEntity(ProjectScorePreviewRequestDTO request, ProjectScorePreviewPolicy.PreviewResult result, String tagsJson, String payloadJson) {
        return ProjectScorePreview.builder()
            .projectId(request.getProjectId())
            .tenderId(request.getTenderId())
            .projectName(request.getProjectName())
            .industry(request.getIndustry())
            .budget(request.getBudget())
            .tagsJson(tagsJson)
            .winScore(result.winScore())
            .winLevel(result.winLevel())
            .payloadJson(payloadJson)
            .build();
    }

    static AiAnalysisJob.JobStatus toEntityStatus(AiJobLifecyclePolicy.JobStatus status) {
        return AiAnalysisJob.JobStatus.valueOf(status.name());
    }

    static TenderAiAnalysisPolicy.RiskLevel toCoreRiskLevel(Tender.RiskLevel riskLevel) {
        if (riskLevel == null) {
            return TenderAiAnalysisPolicy.RiskLevel.MEDIUM;
        }
        return TenderAiAnalysisPolicy.RiskLevel.valueOf(riskLevel.name());
    }

    static String writeValue(Object payload, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize AI payload", e);
        }
    }
}
