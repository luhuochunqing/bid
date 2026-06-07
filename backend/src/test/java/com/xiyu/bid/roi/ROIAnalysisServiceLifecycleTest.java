package com.xiyu.bid.roi;

import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.roi.dto.ROIAnalysisCreateRequest;
import com.xiyu.bid.roi.dto.ROIAnalysisDTO;
import com.xiyu.bid.roi.entity.ROIAnalysis;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ROIAnalysisServiceLifecycleTest extends AbstractROIAnalysisServiceTest {

    @Test
    void createAnalysis_WithValidData_ShouldReturnSavedAnalysis() {
        when(roiAnalysisRepository.save(any(ROIAnalysis.class))).thenReturn(testROIAnalysis);

        ROIAnalysisDTO result = roiAnalysisService.createAnalysis(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getProjectId()).isEqualTo(100L);
        assertThat(result.getEstimatedCost()).isEqualByComparingTo("500000.00");
        assertThat(result.getEstimatedRevenue()).isEqualByComparingTo("800000.00");
        assertThat(result.getEstimatedProfit()).isEqualByComparingTo("300000.00");
        assertThat(result.getRoiPercentage()).isEqualByComparingTo("60.00");
        assertThat(result.getPaybackPeriodMonths()).isEqualTo(24);
        verify(roiAnalysisRepository).save(any(ROIAnalysis.class));
    }

    @Test
    void createAnalysis_WithNullProjectId_ShouldThrowException() {
        ROIAnalysisCreateRequest invalidRequest = ROIAnalysisCreateRequest.builder()
                .projectId(null)
                .estimatedCost(new BigDecimal("500000.00"))
                .estimatedRevenue(new BigDecimal("800000.00"))
                .createdBy(1L)
                .build();

        assertThatThrownBy(() -> roiAnalysisService.createAnalysis(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project ID");
    }

    @Test
    void createAnalysis_WithNullCost_ShouldThrowException() {
        ROIAnalysisCreateRequest invalidRequest = ROIAnalysisCreateRequest.builder()
                .projectId(100L)
                .estimatedCost(null)
                .estimatedRevenue(new BigDecimal("800000.00"))
                .createdBy(1L)
                .build();

        assertThatThrownBy(() -> roiAnalysisService.createAnalysis(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Estimated cost");
    }

    @Test
    void createAnalysis_WithNegativeCost_ShouldThrowException() {
        ROIAnalysisCreateRequest invalidRequest = ROIAnalysisCreateRequest.builder()
                .projectId(100L)
                .estimatedCost(new BigDecimal("-100000.00"))
                .estimatedRevenue(new BigDecimal("800000.00"))
                .createdBy(1L)
                .build();

        assertThatThrownBy(() -> roiAnalysisService.createAnalysis(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Estimated cost");
    }

    @Test
    void createAnalysis_WithNullRevenue_ShouldThrowException() {
        ROIAnalysisCreateRequest invalidRequest = ROIAnalysisCreateRequest.builder()
                .projectId(100L)
                .estimatedCost(new BigDecimal("500000.00"))
                .estimatedRevenue(null)
                .createdBy(1L)
                .build();

        assertThatThrownBy(() -> roiAnalysisService.createAnalysis(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Estimated revenue");
    }

    @Test
    void createAnalysis_WithNegativeRevenue_ShouldThrowException() {
        ROIAnalysisCreateRequest invalidRequest = ROIAnalysisCreateRequest.builder()
                .projectId(100L)
                .estimatedCost(new BigDecimal("500000.00"))
                .estimatedRevenue(new BigDecimal("-100000.00"))
                .createdBy(1L)
                .build();

        assertThatThrownBy(() -> roiAnalysisService.createAnalysis(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Estimated revenue");
    }

    @Test
    void createAnalysis_ShouldAutoCalculateProfitAndROI() {
        ROIAnalysisCreateRequest request = ROIAnalysisCreateRequest.builder()
                .projectId(100L)
                .estimatedCost(new BigDecimal("500000.00"))
                .estimatedRevenue(new BigDecimal("850000.00"))
                .paybackPeriodMonths(24)
                .createdBy(1L)
                .build();
        ROIAnalysis calculatedAnalysis = ROIAnalysis.builder()
                .id(1L)
                .projectId(100L)
                .estimatedCost(new BigDecimal("500000.00"))
                .estimatedRevenue(new BigDecimal("850000.00"))
                .estimatedProfit(new BigDecimal("350000.00"))
                .roiPercentage(new BigDecimal("70.00"))
                .paybackPeriodMonths(24)
                .build();
        when(roiAnalysisRepository.save(any(ROIAnalysis.class))).thenReturn(calculatedAnalysis);

        ROIAnalysisDTO result = roiAnalysisService.createAnalysis(request);

        assertThat(result.getEstimatedProfit()).isEqualByComparingTo("350000.00");
        assertThat(result.getRoiPercentage()).isEqualByComparingTo("70.00");
    }

    @Test
    void createAnalysis_WithZeroCost_ShouldThrowException() {
        ROIAnalysisCreateRequest invalidRequest = ROIAnalysisCreateRequest.builder()
                .projectId(100L)
                .estimatedCost(BigDecimal.ZERO)
                .estimatedRevenue(new BigDecimal("800000.00"))
                .createdBy(1L)
                .build();

        assertThatThrownBy(() -> roiAnalysisService.createAnalysis(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Estimated cost must be greater than zero");
    }

    @Test
    void getAnalysisByProject_WithValidProjectId_ShouldReturnAnalysis() {
        when(roiAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(100L)).thenReturn(Optional.of(testROIAnalysis));

        ROIAnalysisDTO result = roiAnalysisService.getAnalysisByProject(100L);

        assertThat(result).isNotNull();
        assertThat(result.getProjectId()).isEqualTo(100L);
        assertThat(result.getEstimatedCost()).isEqualByComparingTo("500000.00");
        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(100L);
    }

    @Test
    void getAnalysisByProject_WithInvalidProjectId_ShouldThrowException() {
        when(roiAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roiAnalysisService.getAnalysisByProject(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ROI analysis not found");
    }

    @Test
    void getAnalysisByProject_WithNullProjectId_ShouldThrowException() {
        assertThatThrownBy(() -> roiAnalysisService.getAnalysisByProject(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project ID");
    }

    @Test
    void calculateROI_WithValidProjectId_ShouldReturnCalculatedROI() {
        when(roiAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(100L)).thenReturn(Optional.empty());
        when(roiAnalysisRepository.save(any(ROIAnalysis.class))).thenReturn(testROIAnalysis);

        ROIAnalysisDTO result = roiAnalysisService.calculateROI(100L, createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getProjectId()).isEqualTo(100L);
        assertThat(result.getEstimatedProfit()).isEqualByComparingTo("300000.00");
        assertThat(result.getRoiPercentage()).isEqualByComparingTo("60.00");
        verify(roiAnalysisRepository).save(any(ROIAnalysis.class));
    }

    @Test
    void calculateROI_WithExistingAnalysis_ShouldUpdateExisting() {
        when(roiAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(100L)).thenReturn(Optional.of(testROIAnalysis));
        when(roiAnalysisRepository.save(any(ROIAnalysis.class))).thenReturn(testROIAnalysis);

        ROIAnalysisDTO result = roiAnalysisService.calculateROI(100L, createRequest);

        assertThat(result).isNotNull();
        verify(roiAnalysisRepository).save(any(ROIAnalysis.class));
    }

    @Test
    void calculateROI_WithNullProjectId_ShouldThrowException() {
        assertThatThrownBy(() -> roiAnalysisService.calculateROI(null, createRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project ID");
    }

    @Test
    void calculateROI_WithNullRequest_ShouldThrowException() {
        assertThatThrownBy(() -> roiAnalysisService.calculateROI(100L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request");
    }
}
