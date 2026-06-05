package com.xiyu.bid.competitionintel;

import com.xiyu.bid.competitionintel.dto.AnalysisCreateRequest;
import com.xiyu.bid.competitionintel.dto.CompetitionAnalysisDTO;
import com.xiyu.bid.competitionintel.entity.CompetitionAnalysis;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompetitionIntelAnalysisServiceTest extends AbstractCompetitionIntelServiceTest {

    @Test
    void createAnalysis_WithValidData_ShouldReturnSavedAnalysis() {
        CompetitionAnalysis savedAnalysis = CompetitionAnalysis.builder()
                .id(2L)
                .projectId(analysisRequest.getProjectId())
                .competitorId(analysisRequest.getCompetitorId())
                .winProbability(analysisRequest.getWinProbability())
                .competitiveAdvantage(analysisRequest.getCompetitiveAdvantage())
                .recommendedStrategy(analysisRequest.getRecommendedStrategy())
                .riskFactors(analysisRequest.getRiskFactors())
                .analysisDate(LocalDateTime.now())
                .build();
        when(analysisRepository.save(any(CompetitionAnalysis.class))).thenReturn(savedAnalysis);

        CompetitionAnalysisDTO result = service.createAnalysis(analysisRequest);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getProjectId()).isEqualTo(100L);
        assertThat(result.getWinProbability()).isEqualByComparingTo("70.0");
        verify(analysisRepository).save(any(CompetitionAnalysis.class));
    }

    @Test
    void createAnalysis_WithNullProjectId_ShouldThrowException() {
        AnalysisCreateRequest invalidRequest = AnalysisCreateRequest.builder()
                .projectId(null)
                .competitorId(1L)
                .build();

        assertThatThrownBy(() -> service.createAnalysis(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project ID is required");

        verify(analysisRepository, never()).save(any(CompetitionAnalysis.class));
    }

    @Test
    void createAnalysis_WithInvalidWinProbability_ShouldThrowException() {
        AnalysisCreateRequest invalidRequest = AnalysisCreateRequest.builder()
                .projectId(100L)
                .winProbability(new BigDecimal("-10"))
                .build();

        assertThatThrownBy(() -> service.createAnalysis(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Win probability must be between 0 and 100");

        verify(analysisRepository, never()).save(any(CompetitionAnalysis.class));
    }

    @Test
    void createAnalysis_WithWinProbabilityOver100_ShouldThrowException() {
        AnalysisCreateRequest invalidRequest = AnalysisCreateRequest.builder()
                .projectId(100L)
                .winProbability(new BigDecimal("150"))
                .build();

        assertThatThrownBy(() -> service.createAnalysis(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Win probability must be between 0 and 100");

        verify(analysisRepository, never()).save(any(CompetitionAnalysis.class));
    }

    @Test
    void getAnalysisByProject_WithValidProjectId_ShouldReturnAnalysisList() {
        CompetitionAnalysis analysis2 = CompetitionAnalysis.builder()
                .id(2L)
                .projectId(100L)
                .competitorId(2L)
                .winProbability(new BigDecimal("40.0"))
                .build();
        when(analysisRepository.findByProjectId(100L)).thenReturn(Arrays.asList(testAnalysis, analysis2));

        List<CompetitionAnalysisDTO> result = service.getAnalysisByProject(100L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProjectId()).isEqualTo(100L);
        assertThat(result.get(1).getProjectId()).isEqualTo(100L);
        verify(analysisRepository).findByProjectId(100L);
    }

    @Test
    void getAnalysisByProject_WithEmptyResult_ShouldReturnEmptyList() {
        when(analysisRepository.findByProjectId(999L)).thenReturn(List.of());

        List<CompetitionAnalysisDTO> result = service.getAnalysisByProject(999L);

        assertThat(result).isEmpty();
        verify(analysisRepository).findByProjectId(999L);
    }

    @Test
    void getAnalysisByProject_WithNullProjectId_ShouldThrowException() {
        assertThatThrownBy(() -> service.getAnalysisByProject(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project ID is required");

        verify(analysisRepository, never()).findByProjectId(any());
    }

    @Test
    void getHistoricalPerformance_WithValidCompetitorId_ShouldReturnAnalysisList() {
        CompetitionAnalysis oldAnalysis = CompetitionAnalysis.builder()
                .id(2L)
                .projectId(90L)
                .competitorId(1L)
                .winProbability(new BigDecimal("55.0"))
                .analysisDate(LocalDateTime.now().minusMonths(6))
                .build();
        when(analysisRepository.findByCompetitorIdOrderByAnalysisDateDesc(1L))
                .thenReturn(Arrays.asList(testAnalysis, oldAnalysis));

        List<CompetitionAnalysisDTO> result = service.getHistoricalPerformance(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCompetitorId()).isEqualTo(1L);
        assertThat(result.get(1).getCompetitorId()).isEqualTo(1L);
        verify(analysisRepository).findByCompetitorIdOrderByAnalysisDateDesc(1L);
    }

    @Test
    void getHistoricalPerformance_WithEmptyResult_ShouldReturnEmptyList() {
        when(analysisRepository.findByCompetitorIdOrderByAnalysisDateDesc(999L)).thenReturn(List.of());

        List<CompetitionAnalysisDTO> result = service.getHistoricalPerformance(999L);

        assertThat(result).isEmpty();
        verify(analysisRepository).findByCompetitorIdOrderByAnalysisDateDesc(999L);
    }

    @Test
    void getHistoricalPerformance_WithNullCompetitorId_ShouldThrowException() {
        assertThatThrownBy(() -> service.getHistoricalPerformance(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Competitor ID is required");

        verify(analysisRepository, never()).findByCompetitorIdOrderByAnalysisDateDesc(any());
    }

    @Test
    void analyzeCompetition_WithValidProjectId_ShouldReturnNewAnalysis() {
        CompetitionAnalysis newAnalysis = CompetitionAnalysis.builder()
                .id(3L)
                .projectId(100L)
                .analysisDate(LocalDateTime.now())
                .winProbability(new BigDecimal("60.0"))
                .competitiveAdvantage("自动生成的优势分析")
                .recommendedStrategy("自动生成的策略建议")
                .riskFactors("自动生成的风险因素")
                .build();
        when(analysisRepository.save(any(CompetitionAnalysis.class))).thenReturn(newAnalysis);

        CompetitionAnalysisDTO result = service.analyzeCompetition(100L);

        assertThat(result).isNotNull();
        assertThat(result.getProjectId()).isEqualTo(100L);
        assertThat(result.getWinProbability()).isEqualByComparingTo("60.0");
        assertThat(result.getAnalysisDate()).isNotNull();
        verify(analysisRepository).save(any(CompetitionAnalysis.class));
    }

    @Test
    void analyzeCompetition_WithNullProjectId_ShouldThrowException() {
        assertThatThrownBy(() -> service.analyzeCompetition(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project ID is required");

        verify(analysisRepository, never()).save(any(CompetitionAnalysis.class));
    }

    @Test
    void analyzeCompetition_WithRepositoryError_ShouldPropagateException() {
        when(analysisRepository.save(any(CompetitionAnalysis.class)))
                .thenThrow(new RuntimeException("Database error"));

        assertThatThrownBy(() -> service.analyzeCompetition(100L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database error");

        verify(analysisRepository).save(any(CompetitionAnalysis.class));
    }
}
