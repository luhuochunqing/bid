// Input: scoreanalysis repositories, DTOs, and support services
// Output: Score Analysis business service operations
// Pos: Service/业务层
package com.xiyu.bid.scoreanalysis.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.scoreanalysis.core.ScoreAnalysisCalculationPolicy;
import com.xiyu.bid.scoreanalysis.dto.ScoreAnalysisCreateRequest;
import com.xiyu.bid.scoreanalysis.dto.ScoreAnalysisDTO;
import com.xiyu.bid.scoreanalysis.entity.DimensionScore;
import com.xiyu.bid.scoreanalysis.entity.ScoreAnalysis;
import com.xiyu.bid.scoreanalysis.repository.DimensionScoreRepository;
import com.xiyu.bid.scoreanalysis.repository.ScoreAnalysisRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.security.CurrentUserResolver;
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
    private final ScoreAnalysisQueryService queryService;
    private final CurrentUserResolver currentUserResolver;
    private final TenderRepository tenderRepository;

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
                BigDecimal totalScore = ScoreAnalysisCalculationPolicy.calculateWeightedScoreFromDTOs(request.getDimensions());
                analysis.setOverallScore(totalScore.intValue());
                analysis.setRiskLevel(ScoreAnalysisCalculationPolicy.determineRiskLevel(totalScore.intValue()));
            }

            ScoreAnalysis savedAnalysis = scoreAnalysisRepository.save(analysis);

            if (request.getTenderId() != null) {
                try {
                    Long operatorId = currentUserResolver.getCurrentUserId();
                    // CO-571 Phase C: 无当前用户时用 tender.creatorId 兜底
                    //（Phase B 入队阶段 resolveDeliveryUsername 会再按 creatorId → PM → event 顺序解析 username）
                    if (operatorId == null) {
                        operatorId = tenderRepository.findById(request.getTenderId())
                                .map(Tender::getCreatorId).orElse(null);
                    }
                    if (operatorId != null) {
                        tenderCommandService.updateStatus(request.getTenderId(), com.xiyu.bid.entity.Tender.Status.EVALUATED, operatorId);
                    } else {
                        log.warn("跳过标讯状态更新：无可用 operatorId，tenderId: {}", request.getTenderId());
                    }
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

            BigDecimal totalScore = ScoreAnalysisCalculationPolicy.calculateWeightedScoreFromEntities(dimensions);
            analysis.setOverallScore(totalScore.intValue());
            analysis.setRiskLevel(ScoreAnalysisCalculationPolicy.determineRiskLevel(totalScore.intValue()));
            scoreAnalysisRepository.save(analysis);

            return ApiResponse.success(totalScore.intValue());

        } catch (RuntimeException e) {
            log.error("计算综合评分失败: {}", e.getMessage(), e);
            return ApiResponse.error("计算综合评分失败: " + e.getMessage());
        }
    }

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
