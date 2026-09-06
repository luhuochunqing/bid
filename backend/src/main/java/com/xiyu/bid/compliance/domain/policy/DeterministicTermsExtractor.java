// Input: 招标文件正文
// Output: 确定性商务条款结构化契约（BusinessTermsRequirement）
// Pos: compliance/domain/policy/ - 确定性商务条款提取器（FP-Java 纯核心，严格 <= 300 行）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯核心确定性商务条款提取器.
 */
public final class DeterministicTermsExtractor {

    private static final Pattern SCHEDULE_PATTERN = Pattern.compile(
            "(?:工期|交货期|服务期|实施周期)(?:要求|期限)?[：:\\s]{1,10}(?:不超过|少于|自合同签订之日起)?\\s*([0-9]{1,4})\\s*(?:个)?(?:日历天|天|工作日)"
    );

    private static final Pattern WARRANTY_YEAR_PATTERN = Pattern.compile(
            "(?:质保期|保修期|免费维护期|服务保障期)(?:要求)?[：:\\s]{1,10}(?:不少于|不低于|至少)?\\s*([0-9]{1,2})\\s*(?:个)?年"
    );

    private static final Pattern WARRANTY_MONTH_PATTERN = Pattern.compile(
            "(?:质保期|保修期|免费维护期|服务保障期)(?:要求)?[：:\\s]{1,10}(?:不少于|不低于|至少)?\\s*([0-9]{1,3})\\s*(?:个)?(?:月|个月)"
    );

    private static final Pattern VALIDITY_PATTERN = Pattern.compile(
            "投标有效期[：:\\s]{1,10}(?:不少于|不低于)?\\s*([0-9]{1,3})\\s*(?:个)?(?:日历天|天)"
    );

    private static final Pattern PERFORMANCE_BOND_PATTERN = Pattern.compile(
            "(?:履约担保|履约保证金)(?:金额|比例)?[：:\\s]{1,10}.{0,30}?([0-9]{1,2}(?:\\.[0-9]+)?\\s*%)"
    );

    private DeterministicTermsExtractor() {
    }

    public static BusinessTermsRequirement extract(final String tenderText) {
        if (tenderText == null || tenderText.isBlank()) {
            return null;
        }

        Integer maxScheduleDays = null;
        String scheduleRaw = null;
        Matcher mSched = SCHEDULE_PATTERN.matcher(tenderText);
        if (mSched.find()) {
            maxScheduleDays = parseSafeInt(mSched.group(1));
            if (maxScheduleDays != null) {
                scheduleRaw = mSched.group(0).trim();
            }
        }

        Integer minWarrantyMonths = null;
        String warrantyRaw = null;
        Matcher mWarYear = WARRANTY_YEAR_PATTERN.matcher(tenderText);
        if (mWarYear.find()) {
            Integer years = parseSafeInt(mWarYear.group(1));
            if (years != null) {
                minWarrantyMonths = years * 12;
                warrantyRaw = mWarYear.group(0).trim();
            }
        } else {
            Matcher mWarMonth = WARRANTY_MONTH_PATTERN.matcher(tenderText);
            if (mWarMonth.find()) {
                minWarrantyMonths = parseSafeInt(mWarMonth.group(1));
                if (minWarrantyMonths != null) {
                    warrantyRaw = mWarMonth.group(0).trim();
                }
            }
        }

        Integer bidValidityDays = null;
        String bidValidityRaw = null;
        Matcher mVal = VALIDITY_PATTERN.matcher(tenderText);
        if (mVal.find()) {
            bidValidityDays = parseSafeInt(mVal.group(1));
            if (bidValidityDays != null) {
                bidValidityRaw = mVal.group(0).trim();
            }
        }

        String performanceBond = null;
        Matcher mBond = PERFORMANCE_BOND_PATTERN.matcher(tenderText);
        if (mBond.find()) {
            performanceBond = mBond.group(1).trim();
        }

        String paymentTerms = extractPaymentTerms(tenderText);

        StringBuilder desc = new StringBuilder("招标文件商务要求：");
        if (scheduleRaw != null) desc.append(scheduleRaw).append("；");
        if (warrantyRaw != null) desc.append(warrantyRaw).append("；");
        if (bidValidityRaw != null) desc.append(bidValidityRaw).append("；");
        if (performanceBond != null) desc.append("履约保证金: ").append(performanceBond).append("；");

        return new BusinessTermsRequirement(
                true,
                maxScheduleDays,
                scheduleRaw,
                minWarrantyMonths,
                warrantyRaw,
                bidValidityDays,
                bidValidityRaw,
                paymentTerms,
                performanceBond,
                desc.toString()
        );
    }

    private static String extractPaymentTerms(final String text) {
        int idx = text.indexOf("付款方式");
        if (idx < 0) idx = text.indexOf("结算方式");
        if (idx < 0) idx = text.indexOf("款项支付");
        if (idx >= 0) {
            int end = Math.min(text.length(), idx + 100);
            return text.substring(idx, end).replaceAll("[\\r\\n]+", " ").trim();
        }
        return null;
    }

    private static Integer parseSafeInt(final String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String clean = s.trim();
        if (!clean.matches("^[0-9]{1,9}$")) {
            return null;
        }
        long val = 0;
        for (int i = 0; i < clean.length(); i++) {
            val = val * 10 + (clean.charAt(i) - '0');
        }
        return val <= Integer.MAX_VALUE ? (int) val : null;
    }
}
