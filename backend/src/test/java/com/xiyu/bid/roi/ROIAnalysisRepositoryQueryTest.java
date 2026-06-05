package com.xiyu.bid.roi;

import com.xiyu.bid.roi.entity.ROIAnalysis;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ROIAnalysisRepositoryQueryTest extends AbstractROIAnalysisRepositoryTest {

    @Test
    void findById_WithExistingId_ShouldReturnAnalysis() {
        ROIAnalysis saved = roiAnalysisRepository.save(testAnalysis);

        Optional<ROIAnalysis> found = roiAnalysisRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getProjectId()).isEqualTo(100L);
    }

    @Test
    void findById_WithNonExistingId_ShouldReturnEmpty() {
        Optional<ROIAnalysis> found = roiAnalysisRepository.findById(999L);

        assertThat(found).isEmpty();
    }

    @Test
    void findByProjectId_WithExistingProjectId_ShouldReturnAnalysis() {
        roiAnalysisRepository.save(testAnalysis);

        Optional<ROIAnalysis> found = roiAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(100L);

        assertThat(found).isPresent();
        assertThat(found.get().getProjectId()).isEqualTo(100L);
    }

    @Test
    void findByProjectId_WithNonExistingProjectId_ShouldReturnEmpty() {
        Optional<ROIAnalysis> found = roiAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(999L);

        assertThat(found).isEmpty();
    }

    @Test
    void findByProjectId_WithMultipleAnalysesForSameProject_ShouldReturnLatestAnalysis() {
        roiAnalysisRepository.save(testAnalysis);
        ROIAnalysis secondAnalysis = ROIAnalysis.builder()
                .projectId(100L)
                .analysisDate(LocalDateTime.now())
                .estimatedCost(new BigDecimal("600000.00"))
                .estimatedRevenue(new BigDecimal("900000.00"))
                .estimatedProfit(new BigDecimal("300000.00"))
                .roiPercentage(new BigDecimal("50.00"))
                .paybackPeriodMonths(30)
                .createdBy(2L)
                .build();
        roiAnalysisRepository.save(secondAnalysis);

        Optional<ROIAnalysis> found = roiAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(100L);

        assertThat(found).isPresent();
        assertThat(found.get().getEstimatedCost()).isEqualByComparingTo("600000.00");
        assertThat(found.get().getCreatedBy()).isEqualTo(2L);
    }

    @Test
    void findAll_WithMultipleAnalyses_ShouldReturnAll() {
        roiAnalysisRepository.save(testAnalysis);
        ROIAnalysis secondAnalysis = ROIAnalysis.builder()
                .projectId(101L)
                .analysisDate(LocalDateTime.now())
                .estimatedCost(new BigDecimal("400000.00"))
                .estimatedRevenue(new BigDecimal("700000.00"))
                .estimatedProfit(new BigDecimal("300000.00"))
                .roiPercentage(new BigDecimal("75.00"))
                .paybackPeriodMonths(18)
                .createdBy(1L)
                .build();
        roiAnalysisRepository.save(secondAnalysis);

        List<ROIAnalysis> all = roiAnalysisRepository.findAll();

        assertThat(all).hasSize(2);
    }

    @Test
    void findAll_WithNoAnalyses_ShouldReturnEmptyList() {
        List<ROIAnalysis> all = roiAnalysisRepository.findAll();

        assertThat(all).isEmpty();
    }

    @Test
    void findByProjectId_AfterUpdate_ShouldReturnUpdatedData() {
        ROIAnalysis saved = roiAnalysisRepository.save(testAnalysis);
        saved.setEstimatedCost(new BigDecimal("600000.00"));
        saved.setEstimatedRevenue(new BigDecimal("950000.00"));

        roiAnalysisRepository.save(saved);

        Optional<ROIAnalysis> found = roiAnalysisRepository.findFirstByProjectIdOrderByAnalysisDateDesc(100L);
        assertThat(found).isPresent();
        assertThat(found.get().getEstimatedCost()).isEqualByComparingTo("600000.00");
        assertThat(found.get().getEstimatedRevenue()).isEqualByComparingTo("950000.00");
    }
}
