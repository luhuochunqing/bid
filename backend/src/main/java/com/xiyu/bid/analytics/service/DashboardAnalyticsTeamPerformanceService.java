package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.model.TeamAggregate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
class DashboardAnalyticsTeamPerformanceService {

    int calculatePerformanceScore(TeamAggregate aggregate) {
        if (aggregate == null) {
            return 0;
        }
        double winRate = aggregate.projectCount() == 0 ? 0.0 : (aggregate.wonCount() * 100.0) / aggregate.projectCount();
        double taskCompletionRate = aggregate.totalTaskCount() == 0
                ? 0.0
                : (aggregate.completedTaskCount() * 100.0) / aggregate.totalTaskCount();
        double overduePenalty = aggregate.totalTaskCount() == 0
                ? 0.0
                : (aggregate.overdueTaskCount() * 100.0) / aggregate.totalTaskCount();
        BigDecimal normalized = BigDecimal.valueOf((winRate * 0.45) + (taskCompletionRate * 0.4) + (Math.max(0.0, 100.0 - overduePenalty) * 0.15));
        return normalized.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
    }
}
