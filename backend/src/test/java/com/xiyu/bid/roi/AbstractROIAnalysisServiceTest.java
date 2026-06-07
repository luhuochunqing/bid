package com.xiyu.bid.roi;

import com.xiyu.bid.roi.dto.ROIAnalysisCreateRequest;
import com.xiyu.bid.roi.dto.SensitivityAnalysisRequest;
import com.xiyu.bid.roi.entity.ROIAnalysis;
import com.xiyu.bid.roi.repository.ROIAnalysisRepository;
import com.xiyu.bid.roi.service.ROIAnalysisService;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

@ExtendWith(MockitoExtension.class)
abstract class AbstractROIAnalysisServiceTest {

    @Mock
    protected ROIAnalysisRepository roiAnalysisRepository;

    @Mock
    protected ProjectAccessScopeService projectAccessScopeService;

    protected ROIAnalysisService roiAnalysisService;
    protected ROIAnalysis testROIAnalysis;
    protected ROIAnalysisCreateRequest createRequest;
    protected SensitivityAnalysisRequest sensitivityRequest;

    @BeforeEach
    void setUpROIAnalysisFixture() {
        roiAnalysisService = new ROIAnalysisService(roiAnalysisRepository, projectAccessScopeService);

        testROIAnalysis = ROIAnalysis.builder()
                .id(1L)
                .projectId(100L)
                .analysisDate(LocalDateTime.of(2024, 3, 1, 10, 0))
                .estimatedCost(new BigDecimal("500000.00"))
                .estimatedRevenue(new BigDecimal("800000.00"))
                .estimatedProfit(new BigDecimal("300000.00"))
                .roiPercentage(new BigDecimal("60.00"))
                .paybackPeriodMonths(24)
                .riskFactors("Market volatility, regulatory changes")
                .assumptions("Project completion on time, no cost overruns")
                .createdBy(1L)
                .build();

        createRequest = ROIAnalysisCreateRequest.builder()
                .projectId(100L)
                .estimatedCost(new BigDecimal("500000.00"))
                .estimatedRevenue(new BigDecimal("800000.00"))
                .paybackPeriodMonths(24)
                .riskFactors("Market volatility, regulatory changes")
                .assumptions("Project completion on time, no cost overruns")
                .createdBy(1L)
                .build();

        sensitivityRequest = SensitivityAnalysisRequest.builder()
                .projectId(100L)
                .costVariations(Arrays.asList(-10.0, 0.0, 10.0))
                .revenueVariations(Arrays.asList(-10.0, 0.0, 10.0))
                .build();
    }
}
