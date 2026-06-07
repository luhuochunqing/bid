package com.xiyu.bid.biddraftagent.domain.validation;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 业绩记录匹配器（纯核心）。
 * 匹配要求与合同名/签约主体，三态判断。
 */
public class PerformanceMatcher {

    static final int EXPIRY_WARN_DAYS = 90;

    public record PerformanceMatchItem(
            String requirementText,
            QualificationMatchStatus status,
            String matchedContractName,
            String matchedSigningEntity,
            Integer remainingDays,
            String reason
    ) {}

    public record PerformanceMatchResult(List<PerformanceMatchItem> items) {}

    public PerformanceMatchResult match(
            List<String> requirements,
            List<PerformanceSummary> performanceRecords,
            LocalDate referenceDate) {

        List<PerformanceMatchItem> items = new ArrayList<>();
        if (requirements == null || requirements.isEmpty()) {
            return new PerformanceMatchResult(items);
        }

        for (String req : requirements) {
            PerformanceSummary matched = null;
            for (PerformanceSummary perf : performanceRecords) {
                if (SmartMatchUtils.isSmartMatch(req, perf.contractName())
                        || SmartMatchUtils.isSmartMatch(req, perf.signingEntity())) {
                    matched = perf;
                    break;
                }
            }

            if (matched == null) {
                items.add(new PerformanceMatchItem(
                        req, QualificationMatchStatus.UNSATISFIED,
                        null, null, null,
                        "业绩库中未找到匹配的业绩记录"));
            } else {
                Integer remainingDays = computeRemainingDays(matched, referenceDate);
                if (remainingDays != null && remainingDays <= EXPIRY_WARN_DAYS) {
                    items.add(new PerformanceMatchItem(
                            req, QualificationMatchStatus.ATTENTION,
                            matched.contractName(), matched.signingEntity(), remainingDays,
                            "业绩「" + matched.contractName() + "」" + remainingDays + "天后到期，建议人工复核"));
                } else {
                    items.add(new PerformanceMatchItem(
                            req, QualificationMatchStatus.SATISFIED,
                            matched.contractName(), matched.signingEntity(), remainingDays,
                            "业绩库中已匹配「" + matched.contractName() + "」"));
                }
            }
        }
        return new PerformanceMatchResult(items);
    }

    private Integer computeRemainingDays(PerformanceSummary perf, LocalDate referenceDate) {
        if (perf.expiryDate() == null) return null;
        long days = ChronoUnit.DAYS.between(referenceDate, perf.expiryDate());
        return days < 0 ? null : (int) days;
    }
}
