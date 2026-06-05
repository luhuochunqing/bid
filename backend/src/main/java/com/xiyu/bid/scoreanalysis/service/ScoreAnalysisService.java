// Input: scoreanalysis repositories, DTOs, and support services
// Output: Score Analysis business service operations
// Pos: Service/业务层
package com.xiyu.bid.scoreanalysis.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.scoreanalysis.core.ScoreAnalysisCalculationPolicy;
import com.xiyu.bid.scoreanalysis.dto.ScoreAnalysisCreateRequest;
import com.xiyu.bid.scoreanalysis.dto.ScoreAnalysisDTO;
import com.xiyu.bid.scoreanalysis.entity.DimensionScore;
import com.xiyu.bid.scoreanalysis.entity.ScoreAnalysis;
import com.xiyu.bid.scoreanalysis.repository.DimensionScoreRepository;
import com.xiyu.bid.scoreanalysis.repository.ScoreAnalysisRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 评分分析指令服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoreAnalysisService {

    private final ScoreAnalysisRepository scoreAnalysisRepository;
    private final DimensionScoreRepository dimensionScoreRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final com.xiyu.bid.tender.service.TenderCommandService tenderCommandService;
    private final ScoreAnalysisCalculationPolicy calculationPolicy;
    private final ScoreAnalysisQueryService queryService;

    @Auditable(action = "CREATE", entityType = "ScoreAnalysis", description = "创建评分分析")
    @Transactional
    public ApiResponse<ScoreAnalysisDTO> createAnalysis(ScoreAnalysisCreateRequest request) {
        try {
            if (request.getProjectId() != null) {
                projectAccessScopeService.assertCurrentUserCanAccessProject(request.getProjectId());
            }

            ScoreAnalysis analysis = ScoreAnalysis.builder()
                    .projectId(request.getProjectId())
                    .tenderId(request.getTenderId())
                    .analysisDate(LocalDateTime.now())
                    .analystId(request.getAnalystId())
                    .isAiGenerated(request.getIsAiGenerated() != null ? request.getIsAiGenerated() : false)
                    .summary(request.getSummary())
                    .build();

            if (request.getDimensions() != null && !request.getDimensions().isEmpty()) {
                BigDecimal totalScore = calculationPolicy.calculateWeightedScoreFromDTOs(request.getDimensions());
                analysis.setOverallScore(totalScore.intValue());
                analysis.setRiskLevel(calculationPolicy.determineRiskLevel(totalScore.intValue()));
            }

            ScoreAnalysis savedAnalysis = scoreAnalysisRepository.save(analysis);

            if (request.getTenderId() != null) {
                try {
                    tenderCommandService.updateStatus(request.getTenderId(), com.xiyu.bid.entity.Tender.Status.EVALUATED);
                } catch (Exception e) {
                    log.warn("更新标讯状态失败, tenderId: {}, error: {}", request.getTenderId(), e.getMessage());
                }
            }

            if (request.getDimensions() != null && !request.getDimensions().isEmpty()) {
                List<DimensionScore> dimensions = request.getDimensions().stream()
                        .map(dto -> DimensionScore.builder()
                                .analysisId(savedAnalysis.getId())
                                .dimensionName(dto.getDimensionName())
                                .score(dto.getScore())
                                .weight(dto.getWeight())
                                .comments(dto.getComments())
                                .build())
                        .collect(Collectors.toList());
                dimensionScoreRepository.saveAll(dimensions);
            }

            return ApiResponse.success("评分分析创建成功", queryService.convertToDTO(savedAnalysis));

        } catch (RuntimeException e) {
            log.error("创建评分分析失败: {}", e.getMessage(), e);
            return ApiResponse.error("创建评分分析失败: " + e.getMessage());
        }
    }

    @Transactional
    public ApiResponse<Integer> calculateOverallScore(Long projectId) {
        try {
            projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
            Optional<ScoreAnalysis> analysisOpt = scoreAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(projectId);

            if (analysisOpt.isEmpty()) return ApiResponse.error("未找到项目的评分分析");

            ScoreAnalysis analysis = analysisOpt.get();
            List<DimensionScore> dimensions = dimensionScoreRepository.findByAnalysisId(analysis.getId());

            if (dimensions.isEmpty()) return ApiResponse.success("综合评分计算成功", analysis.getOverallScore());

            BigDecimal totalScore = calculationPolicy.calculateWeightedScoreFromEntities(dimensions);
            analysis.setOverallScore(totalScore.intValue());
            analysis.setRiskLevel(calculationPolicy.determineRiskLevel(totalScore.intValue()));
            scoreAnalysisRepository.save(analysis);

            return ApiResponse.success(totalScore.intValue());

        } catch (RuntimeException e) {
            log.error("计算综合评分失败: {}", e.getMessage(), e);
            return ApiResponse.error("计算综合评分失败: " + e.getMessage());
        }
    }

    // Delegation to query service for backward compatibility if needed, 
    // or just remove them if controller is updated.
    // I'll keep them as delegates for now to avoid breaking the controller.

    public ApiResponse<ScoreAnalysisDTO> getAnalysisByProject(Long projectId) {
        return queryService.getAnalysisByProject(projectId);
    }

    public ApiResponse<List<ScoreAnalysisDTO>> getAnalysisHistory(Long projectId) {
        return queryService.getAnalysisHistory(projectId);
    }

    public ApiResponse<ScoreAnalysisDTO> getLatestAnalysis(Long projectId) {
        return queryService.getLatestAnalysis(projectId);
    }

    public ApiResponse<List<ScoreAnalysisDTO>> compareProjects(Long projectId1, Long projectId2) {
        return queryService.compareProjects(projectId1, projectId2);
    }
}
