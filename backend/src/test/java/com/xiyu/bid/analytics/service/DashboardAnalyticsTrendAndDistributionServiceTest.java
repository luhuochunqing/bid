package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardAnalyticsTrendAndDistributionServiceTest {

    private final DashboardAnalyticsTrendAndDistributionService service = new DashboardAnalyticsTrendAndDistributionService();

    @Test
    void calculateSuccessRate_capsAtHundredWhenWinningProjectsExceedBiddedTenders() {
        DashboardAnalyticsRepository.OverviewSnapshot snapshot =
                new DashboardAnalyticsRepository.OverviewSnapshot(
                        100L, BigDecimal.ZERO, 50L, 0L, 5L, 20L
                );

        double successRate = service.calculateSuccessRate(snapshot);

        assertThat(successRate).isEqualTo(100.0);
    }

    @Test
    void calculateSuccessRate_returnsZeroWhenNoBiddedTender() {
        DashboardAnalyticsRepository.OverviewSnapshot snapshot =
                new DashboardAnalyticsRepository.OverviewSnapshot(
                        100L, BigDecimal.ZERO, 50L, 0L, 0L, 20L
                );

        double successRate = service.calculateSuccessRate(snapshot);

        assertThat(successRate).isEqualTo(0.0);
    }
}
