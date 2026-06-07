package com.xiyu.bid.roi;

import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.roi.dto.ROIAnalysisCreateRequest;
import com.xiyu.bid.roi.dto.ROIAnalysisDTO;
import com.xiyu.bid.roi.dto.SensitivityAnalysisRequest;
import com.xiyu.bid.roi.dto.SensitivityAnalysisResult;
import com.xiyu.bid.roi.entity.ROIAnalysis;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ROIAnalysisSensitivityServiceTest extends AbstractROIAnalysisServiceTest {

    @Test
    void performSensitivityAnalysis_WithValidData_ShouldReturnResults() {
        when(roiAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(100L)).thenReturn(Optional.of(testROIAnalysis));

        SensitivityAnalysisResult result = roiAnalysisService.performSensitivityAnalysis(100L, sensitivityRequest);

        assertThat(result).isNotNull();
        assertThat(result.getProjectId()).isEqualTo(100L);
        assertThat(result.getScenarios()).hasSize(9);
    }

    @Test
    void performSensitivityAnalysis_WithNoExistingAnalysis_ShouldThrowException() {
        when(roiAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roiAnalysisService.performSensitivityAnalysis(999L, sensitivityRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ROI analysis not found");
    }

    @Test
    void performSensitivityAnalysis_WithNullProjectId_ShouldThrowException() {
        assertThatThrownBy(() -> roiAnalysisService.performSensitivityAnalysis(null, sensitivityRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project ID");
    }

    @Test
    void performSensitivityAnalysis_WithNullRequest_ShouldThrowException() {
        assertThatThrownBy(() -> roiAnalysisService.performSensitivityAnalysis(100L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sensitivity analysis request");
    }

    @Test
    void performSensitivityAnalysis_WithEmptyVariations_ShouldThrowException() {
        SensitivityAnalysisRequest invalidRequest = SensitivityAnalysisRequest.builder()
                .projectId(100L)
                .costVariations(Arrays.asList())
                .revenueVariations(Arrays.asList())
                .build();

        assertThatThrownBy(() -> roiAnalysisService.performSensitivityAnalysis(100L, invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("variations");
    }

    @Test
    void performSensitivityAnalysis_ShouldCalculateCorrectROIForScenarios() {
        when(roiAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(100L)).thenReturn(Optional.of(testROIAnalysis));

        SensitivityAnalysisResult result = roiAnalysisService.performSensitivityAnalysis(100L, sensitivityRequest);

        assertThat(result.getScenarios()).isNotEmpty();
        assertThat(result.getScenarios().stream().anyMatch(s -> s.getDescription().contains("Best case"))).isTrue();
        assertThat(result.getScenarios().stream().anyMatch(s -> s.getDescription().contains("Worst case"))).isTrue();
    }

    @Test
    void createAnalysis_WithVeryLargeNumbers_ShouldHandleCorrectly() {
        ROIAnalysisCreateRequest request = ROIAnalysisCreateRequest.builder()
                .projectId(100L)
                .estimatedCost(new BigDecimal("999999999999.99"))
                .estimatedRevenue(new BigDecimal("9999999999999.99"))
                .createdBy(1L)
                .build();
        ROIAnalysis largeAnalysis = ROIAnalysis.builder()
                .id(1L)
                .projectId(100L)
                .estimatedCost(new BigDecimal("999999999999.99"))
                .estimatedRevenue(new BigDecimal("9999999999999.99"))
                .estimatedProfit(new BigDecimal("9000000000000.00"))
                .roiPercentage(new BigDecimal("900.00"))
                .build();
        when(roiAnalysisRepository.save(any(ROIAnalysis.class))).thenReturn(largeAnalysis);

        ROIAnalysisDTO result = roiAnalysisService.createAnalysis(request);

        assertThat(result.getEstimatedCost()).isEqualByComparingTo("999999999999.99");
        assertThat(result.getRoiPercentage()).isEqualByComparingTo("900.00");
    }

    @Test
    void createAnalysis_WithRevenueLowerThanCost_ShouldCalculateNegativeROI() {
        ROIAnalysisCreateRequest request = ROIAnalysisCreateRequest.builder()
                .projectId(100L)
                .estimatedCost(new BigDecimal("800000.00"))
                .estimatedRevenue(new BigDecimal("500000.00"))
                .createdBy(1L)
                .build();
        ROIAnalysis negativeROI = ROIAnalysis.builder()
                .id(1L)
                .projectId(100L)
                .estimatedCost(new BigDecimal("800000.00"))
                .estimatedRevenue(new BigDecimal("500000.00"))
                .estimatedProfit(new BigDecimal("-300000.00"))
                .roiPercentage(new BigDecimal("-37.50"))
                .build();
        when(roiAnalysisRepository.save(any(ROIAnalysis.class))).thenReturn(negativeROI);

        ROIAnalysisDTO result = roiAnalysisService.createAnalysis(request);

        assertThat(result.getEstimatedProfit()).isEqualByComparingTo("-300000.00");
        assertThat(result.getRoiPercentage()).isEqualByComparingTo("-37.50");
    }

    @Test
    void performSensitivityAnalysis_WithSingleVariation_ShouldReturnOneScenario() {
        SensitivityAnalysisRequest singleVariation = SensitivityAnalysisRequest.builder()
                .projectId(100L)
                .costVariations(Arrays.asList(0.0))
                .revenueVariations(Arrays.asList(0.0))
                .build();
        when(roiAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(100L)).thenReturn(Optional.of(testROIAnalysis));

        SensitivityAnalysisResult result = roiAnalysisService.performSensitivityAnalysis(100L, singleVariation);

        assertThat(result.getScenarios()).hasSize(1);
    }
}
