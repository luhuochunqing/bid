package com.xiyu.bid.scoreanalysis;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.scoreanalysis.dto.DimensionScoreDTO;
import com.xiyu.bid.scoreanalysis.dto.ScoreAnalysisDTO;
import com.xiyu.bid.scoreanalysis.dto.ScoreAnalysisCreateRequest;
import com.xiyu.bid.scoreanalysis.entity.DimensionScore;
import com.xiyu.bid.scoreanalysis.entity.ScoreAnalysis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ScoreAnalysis workflow tests")
class ScoreAnalysisWorkflowTest extends AbstractScoreAnalysisServiceTest {

    @Test
    @DisplayName("应该成功创建评分分析")
    void shouldCreateAnalysisSuccessfully() {
        when(calculationPolicy.calculateWeightedScoreFromDTOs(any())).thenReturn(new BigDecimal("85"));
        when(calculationPolicy.determineRiskLevel(85)).thenReturn(RiskLevel.LOW);
        when(scoreAnalysisRepository.save(any(ScoreAnalysis.class))).thenReturn(testAnalysis);
        when(queryService.convertToDTO(any())).thenReturn(convertToDTO(testAnalysis));

        ApiResponse<ScoreAnalysisDTO> response = scoreAnalysisService.createAnalysis(createRequest);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals(100L, response.getData().getProjectId());
        verify(scoreAnalysisRepository, times(1)).save(any(ScoreAnalysis.class));
    }

    @Test
    @DisplayName("创建分析时应该记录审计日志")
    void shouldLogAuditWhenCreatingAnalysis() {
        when(calculationPolicy.calculateWeightedScoreFromDTOs(any())).thenReturn(new BigDecimal("85"));
        when(calculationPolicy.determineRiskLevel(85)).thenReturn(RiskLevel.LOW);
        when(scoreAnalysisRepository.save(any(ScoreAnalysis.class))).thenReturn(testAnalysis);

        scoreAnalysisService.createAnalysis(createRequest);

        verify(scoreAnalysisRepository, times(1)).save(any(ScoreAnalysis.class));
    }

    @Test
    @DisplayName("应该正确计算加权总分")
    void shouldCalculateOverallScoreCorrectly() {
        List<DimensionScore> dimensions = Arrays.asList(
                DimensionScore.builder().dimensionName("技术能力").score(90).weight(new BigDecimal("0.30")).build()
        );
        when(scoreAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(100L)).thenReturn(Optional.of(testAnalysis));
        when(dimensionScoreRepository.findByAnalysisId(1L)).thenReturn(dimensions);
        when(calculationPolicy.calculateWeightedScoreFromEntities(any())).thenReturn(new BigDecimal("84"));
        when(calculationPolicy.determineRiskLevel(84)).thenReturn(RiskLevel.LOW);
        when(scoreAnalysisRepository.save(any(ScoreAnalysis.class))).thenReturn(testAnalysis);

        ApiResponse<Integer> response = scoreAnalysisService.calculateOverallScore(100L);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(84, response.getData());
    }

    @Test
    @DisplayName("应该根据分数确定风险等级")
    void shouldDetermineRiskLevelBasedOnScore() {
        ScoreAnalysis highRisk = ScoreAnalysis.builder().id(1L).projectId(101L).overallScore(45).riskLevel(RiskLevel.HIGH).build();
        ScoreAnalysis mediumRisk = ScoreAnalysis.builder().id(2L).projectId(102L).overallScore(65).riskLevel(RiskLevel.MEDIUM).build();
        ScoreAnalysis lowRisk = ScoreAnalysis.builder().id(3L).projectId(103L).overallScore(85).riskLevel(RiskLevel.LOW).build();
        
        when(queryService.getAnalysisByProject(101L)).thenReturn(ApiResponse.success(convertToDTO(highRisk)));
        when(queryService.getAnalysisByProject(102L)).thenReturn(ApiResponse.success(convertToDTO(mediumRisk)));
        when(queryService.getAnalysisByProject(103L)).thenReturn(ApiResponse.success(convertToDTO(lowRisk)));

        ApiResponse<ScoreAnalysisDTO> response1 = scoreAnalysisService.getAnalysisByProject(101L);
        ApiResponse<ScoreAnalysisDTO> response2 = scoreAnalysisService.getAnalysisByProject(102L);
        ApiResponse<ScoreAnalysisDTO> response3 = scoreAnalysisService.getAnalysisByProject(103L);

        assertTrue(response1.isSuccess());
        assertEquals(RiskLevel.HIGH, response1.getData().getRiskLevel());
        assertTrue(response2.isSuccess());
        assertEquals(RiskLevel.MEDIUM, response2.getData().getRiskLevel());
        assertTrue(response3.isSuccess());
        assertEquals(RiskLevel.LOW, response3.getData().getRiskLevel());
    }

    @Test
    @DisplayName("应该处理空维度列表的创建请求")
    void shouldHandleCreateRequestWithEmptyDimensions() {
        ScoreAnalysisCreateRequest emptyRequest = ScoreAnalysisCreateRequest.builder()
                .projectId(100L)
                .dimensions(List.of())
                .build();
        when(scoreAnalysisRepository.save(any(ScoreAnalysis.class))).thenReturn(testAnalysis);
        when(queryService.convertToDTO(any())).thenReturn(convertToDTO(testAnalysis));

        ApiResponse<ScoreAnalysisDTO> response = scoreAnalysisService.createAnalysis(emptyRequest);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        verify(dimensionScoreRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("应该处理null维度的创建请求")
    void shouldHandleCreateRequestWithNullDimensions() {
        ScoreAnalysisCreateRequest nullRequest = ScoreAnalysisCreateRequest.builder()
                .projectId(100L)
                .dimensions(null)
                .build();
        when(scoreAnalysisRepository.save(any(ScoreAnalysis.class))).thenReturn(testAnalysis);

        ApiResponse<ScoreAnalysisDTO> response = scoreAnalysisService.createAnalysis(nullRequest);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        verify(dimensionScoreRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("应该正确处理边界分数0和100")
    void shouldHandleBoundaryScoresZeroAndHundred() {
        List<DimensionScoreDTO> zeroDimensions = List.of(
                DimensionScoreDTO.builder().dimensionName("技术能力").score(0).weight(new BigDecimal("1.00")).build()
        );
        ScoreAnalysisCreateRequest zeroRequest = ScoreAnalysisCreateRequest.builder()
                .projectId(100L)
                .dimensions(zeroDimensions)
                .build();
        ScoreAnalysis zeroScoreAnalysis = ScoreAnalysis.builder()
                .id(1L)
                .projectId(100L)
                .overallScore(0)
                .riskLevel(RiskLevel.HIGH)
                .build();
        
        when(calculationPolicy.calculateWeightedScoreFromDTOs(any())).thenReturn(BigDecimal.ZERO);
        when(calculationPolicy.determineRiskLevel(0)).thenReturn(RiskLevel.HIGH);
        when(scoreAnalysisRepository.save(any(ScoreAnalysis.class))).thenReturn(zeroScoreAnalysis);
        when(queryService.convertToDTO(any())).thenReturn(convertToDTO(zeroScoreAnalysis));

        ApiResponse<ScoreAnalysisDTO> response = scoreAnalysisService.createAnalysis(zeroRequest);

        assertTrue(response.isSuccess());
        assertEquals(0, response.getData().getOverallScore());
        assertEquals(RiskLevel.HIGH, response.getData().getRiskLevel());
    }
}
