package com.xiyu.bid.scoreanalysis.service;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.scoreanalysis.dto.DimensionScoreDTO;
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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 评分分析查询服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ScoreAnalysisQueryService {

    private final ScoreAnalysisRepository scoreAnalysisRepository;
    private final DimensionScoreRepository dimensionScoreRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    public ApiResponse<ScoreAnalysisDTO> getAnalysisByProject(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return scoreAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(projectId)
                .map(analysis -> ApiResponse.success("获取评分分析成功", convertToDTO(analysis)))
                .orElse(ApiResponse.error("未找到项目的评分分析"));
    }

    public ApiResponse<List<ScoreAnalysisDTO>> getAnalysisHistory(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        List<ScoreAnalysisDTO> dtos = scoreAnalysisRepository.findByProjectIdOrderByAnalysisDateDesc(projectId)
                .stream().map(this::convertToDTO).collect(Collectors.toList());
        return ApiResponse.success("历史分析记录", dtos);
    }

    public ApiResponse<ScoreAnalysisDTO> getLatestAnalysis(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return scoreAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(projectId)
                .map(analysis -> ApiResponse.success("获取最新分析成功", convertToDTO(analysis)))
                .orElse(ApiResponse.error("未找到项目的评分分析"));
    }

    public ApiResponse<List<ScoreAnalysisDTO>> compareProjects(Long projectId1, Long projectId2) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId1);
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId2);
        
        ScoreAnalysis a1 = scoreAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(projectId1).orElse(null);
        ScoreAnalysis a2 = scoreAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(projectId2).orElse(null);

        if (a1 == null) return ApiResponse.error("无法找到项目" + projectId1 + "的评分分析");
        if (a2 == null) return ApiResponse.error("无法找到项目" + projectId2 + "的评分分析");

        return ApiResponse.success("项目比较结果", List.of(convertToDTO(a1), convertToDTO(a2)));
    }

    public ScoreAnalysisDTO convertToDTO(ScoreAnalysis analysis) {
        List<DimensionScore> dimensions = dimensionScoreRepository.findByAnalysisId(analysis.getId());
        List<DimensionScoreDTO> dimensionDTOs = dimensions.stream()
                .map(d -> DimensionScoreDTO.builder()
                        .id(d.getId())
                        .analysisId(d.getAnalysisId())
                        .dimensionName(d.getDimensionName())
                        .score(d.getScore())
                        .weight(d.getWeight())
                        .comments(d.getComments())
                        .build())
                .collect(Collectors.toList());

        return ScoreAnalysisDTO.builder()
                .id(analysis.getId())
                .projectId(analysis.getProjectId())
                .tenderId(analysis.getTenderId())
                .analysisDate(analysis.getAnalysisDate())
                .overallScore(analysis.getOverallScore())
                .riskLevel(analysis.getRiskLevel())
                .analystId(analysis.getAnalystId())
                .isAiGenerated(analysis.getIsAiGenerated())
                .summary(analysis.getSummary())
                .dimensions(dimensionDTOs)
                .build();
    }
}
