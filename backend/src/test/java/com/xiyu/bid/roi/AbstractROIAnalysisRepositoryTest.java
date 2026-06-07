package com.xiyu.bid.roi;

import com.xiyu.bid.roi.entity.ROIAnalysis;
import com.xiyu.bid.roi.repository.ROIAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

abstract class AbstractROIAnalysisRepositoryTest {

    @Autowired
    protected ROIAnalysisRepository roiAnalysisRepository;

    protected ROIAnalysis testAnalysis;

    @BeforeEach
    void setUpROIAnalysisRepositoryFixture() {
        roiAnalysisRepository.deleteAll();

        testAnalysis = ROIAnalysis.builder()
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
    }
}
