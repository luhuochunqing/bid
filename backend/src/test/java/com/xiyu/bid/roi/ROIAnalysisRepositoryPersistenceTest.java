package com.xiyu.bid.roi;

import com.xiyu.bid.roi.entity.ROIAnalysis;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class ROIAnalysisRepositoryPersistenceTest extends AbstractROIAnalysisRepositoryTest {

    @Test
    void save_WithValidAnalysis_ShouldPersistAnalysis() {
        ROIAnalysis saved = roiAnalysisRepository.save(testAnalysis);

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getProjectId()).isEqualTo(100L);
        assertThat(saved.getEstimatedCost()).isEqualByComparingTo("500000.00");
        assertThat(saved.getEstimatedRevenue()).isEqualByComparingTo("800000.00");
    }

    @Test
    void save_WithNullProjectId_ShouldThrowException() {
        testAnalysis.setProjectId(null);

        assertThatThrownBy(() -> roiAnalysisRepository.saveAndFlush(testAnalysis))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void save_WithNegativeCost_ShouldPersist() {
        testAnalysis.setEstimatedCost(new BigDecimal("-100000.00"));

        ROIAnalysis saved = roiAnalysisRepository.save(testAnalysis);

        assertThat(saved.getEstimatedCost()).isEqualByComparingTo("-100000.00");
    }

    @Test
    void deleteById_WithExistingId_ShouldDeleteAnalysis() {
        ROIAnalysis saved = roiAnalysisRepository.save(testAnalysis);
        Long id = saved.getId();

        roiAnalysisRepository.deleteById(id);

        Optional<ROIAnalysis> found = roiAnalysisRepository.findById(id);
        assertThat(found).isEmpty();
    }

    @Test
    void save_WithVeryLargePrecisionValues_ShouldPersistCorrectly() {
        testAnalysis.setEstimatedCost(new BigDecimal("99999999999999.99"));
        testAnalysis.setEstimatedRevenue(new BigDecimal("99999999999999.99"));
        testAnalysis.setRoiPercentage(new BigDecimal("99999.99"));

        ROIAnalysis saved = roiAnalysisRepository.save(testAnalysis);

        assertThat(saved.getEstimatedCost()).isEqualByComparingTo("99999999999999.99");
        assertThat(saved.getEstimatedRevenue()).isEqualByComparingTo("99999999999999.99");
        assertThat(saved.getRoiPercentage()).isEqualByComparingTo("99999.99");
    }

    @Test
    void save_WithNegativeROI_ShouldPersist() {
        testAnalysis.setEstimatedCost(new BigDecimal("800000.00"));
        testAnalysis.setEstimatedRevenue(new BigDecimal("500000.00"));
        testAnalysis.setEstimatedProfit(new BigDecimal("-300000.00"));
        testAnalysis.setRoiPercentage(new BigDecimal("-37.50"));

        ROIAnalysis saved = roiAnalysisRepository.save(testAnalysis);

        assertThat(saved.getRoiPercentage()).isEqualByComparingTo("-37.50");
    }

    @Test
    void save_WithNullOptionalFields_ShouldPersist() {
        testAnalysis.setRiskFactors(null);
        testAnalysis.setAssumptions(null);
        testAnalysis.setPaybackPeriodMonths(null);

        ROIAnalysis saved = roiAnalysisRepository.save(testAnalysis);

        assertThat(saved.getRiskFactors()).isNull();
        assertThat(saved.getAssumptions()).isNull();
        assertThat(saved.getPaybackPeriodMonths()).isNull();
    }

    @Test
    void save_WithEmptyRiskFactorsAndAssumptions_ShouldPersist() {
        testAnalysis.setRiskFactors("");
        testAnalysis.setAssumptions("");

        ROIAnalysis saved = roiAnalysisRepository.save(testAnalysis);

        assertThat(saved.getRiskFactors()).isEmpty();
        assertThat(saved.getAssumptions()).isEmpty();
    }

    @Test
    void save_WithVeryLongRiskFactors_ShouldTruncate() {
        testAnalysis.setRiskFactors("A".repeat(10000));

        ROIAnalysis saved = roiAnalysisRepository.save(testAnalysis);

        assertThat(saved.getRiskFactors()).isNotNull();
        assertThat(saved.getRiskFactors().length()).isLessThanOrEqualTo(10000);
    }
}
