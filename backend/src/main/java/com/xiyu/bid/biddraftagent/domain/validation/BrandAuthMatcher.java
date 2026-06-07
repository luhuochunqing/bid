package com.xiyu.bid.biddraftagent.domain.validation;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 品牌授权匹配器（纯核心）。
 * 匹配要求与品牌名/产品线/制造商名，三态判断。
 */
public class BrandAuthMatcher {

    static final int EXPIRY_WARN_DAYS = 90;

    public record BrandAuthMatchItem(
            String requirementText,
            QualificationMatchStatus status,
            String matchedBrandName,
            String matchedProductLine,
            Integer remainingDays,
            String reason
    ) {}

    public record BrandAuthMatchResult(List<BrandAuthMatchItem> items) {}

    public BrandAuthMatchResult match(
            List<String> requirements,
            List<BrandAuthSummary> brandAuths,
            LocalDate referenceDate) {

        List<BrandAuthMatchItem> items = new ArrayList<>();
        if (requirements == null || requirements.isEmpty()) {
            return new BrandAuthMatchResult(items);
        }

        for (String req : requirements) {
            BrandAuthSummary matched = null;
            for (BrandAuthSummary auth : brandAuths) {
                if (SmartMatchUtils.isSmartMatch(req, auth.brandName())
                        || SmartMatchUtils.isSmartMatch(req, auth.productLine())
                        || SmartMatchUtils.isSmartMatch(req, auth.manufacturerName())) {
                    matched = auth;
                    break;
                }
            }

            if (matched == null) {
                items.add(new BrandAuthMatchItem(
                        req, QualificationMatchStatus.UNSATISFIED,
                        null, null, null,
                        "品牌授权库中未找到匹配项"));
            } else {
                Integer remainingDays = computeRemainingDays(matched, referenceDate);
                if (remainingDays != null && remainingDays <= EXPIRY_WARN_DAYS) {
                    items.add(new BrandAuthMatchItem(
                            req, QualificationMatchStatus.ATTENTION,
                            matched.brandName(), matched.productLine(), remainingDays,
                            "品牌授权「" + matched.brandName() + " - " + matched.productLine()
                                    + "」" + remainingDays + "天后到期，建议人工复核"));
                } else {
                    items.add(new BrandAuthMatchItem(
                            req, QualificationMatchStatus.SATISFIED,
                            matched.brandName(), matched.productLine(), remainingDays,
                            "品牌授权库中已匹配「" + matched.brandName() + " - " + matched.productLine() + "」"));
                }
            }
        }
        return new BrandAuthMatchResult(items);
    }

    private Integer computeRemainingDays(BrandAuthSummary auth, LocalDate referenceDate) {
        if (auth.authEndDate() == null) return null;
        long days = ChronoUnit.DAYS.between(referenceDate, auth.authEndDate());
        return days < 0 ? null : (int) days;
    }
}
