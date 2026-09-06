// Input: 投标文件分页内容 (DocumentContent) + 招标商务要求 (BusinessTermsRequirement)
// Output: 工期与商务条款一致性核查结果 (QualityCheckItemResult: PASS / FAIL / MANUAL_REVIEW)
// Pos: compliance/domain/policy/ - 商务条款与工期响应一致性核查策略（纯核心 FP-Java，严格 <= 300 行）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 商务条款与工期响应一致性核查策略 (FP-Java 纯核心).
 */
public final class TermConsistencyCheckPolicy {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "(?:工期|交货期|质保期|保修期|投标有效期)[：:\\s为]{1,10}(?:_{3,}|【(?:请填写|此处填写|待填).*?】|待定)"
                    + "|_{3,}\\s*(?:日历天|天|个月|年)"
    );

    private static final Pattern SCHEDULE_DAYS_PATTERN = Pattern.compile(
            "(?:工期|交货期|服务期|实施周期|承诺工期|工期承诺)(?:要求|期限|承诺)?[：:\\s为]{1,10}(?:承诺自.*?之日起)?(?:为)?\\s*([0-9]{1,4})\\s*(?:个)?(?:日历天|天|工作日)"
    );

    private static final Pattern WARRANTY_YEAR_PATTERN = Pattern.compile(
            "(?:质保期|保修期|免费维护期|质保承诺|质保)(?:要求|期限|承诺)?[：:\\s为]{1,10}(?:不少于|提供|为)?\\s*([0-9]{1,2})\\s*(?:个)?年"
    );

    private static final Pattern WARRANTY_MONTH_PATTERN = Pattern.compile(
            "(?:质保期|保修期|免费维护期|质保承诺|质保)(?:要求|期限|承诺)?[：:\\s为]{1,10}(?:不少于|提供|为)?\\s*([0-9]{1,3})\\s*(?:个)?(?:月|个月)"
    );

    private static final Pattern VALIDITY_DAYS_PATTERN = Pattern.compile(
            "投标有效期(?:承诺)?[：:\\s为]{1,10}(?:为)?\\s*([0-9]{1,3})\\s*(?:个)?(?:日历天|天)"
    );

    private TermConsistencyCheckPolicy() {
    }

    // REUSE-OK: * — 质检商务条款一致性核查入口策略，业务域独立
    public static QualityCheckItemResult check(final DocumentContent bidDoc) {
        return check(bidDoc, null);
    }

    // REUSE-OK: * — 质检商务条款一致性带契约核查入口策略，业务域独立
    public static QualityCheckItemResult check(
            final DocumentContent bidDoc, final BusinessTermsRequirement requirement) {
        BidQualityCheckItem item = BidQualityCheckItem.SCHEDULE_RESPONSE;
        if (bidDoc == null || bidDoc.pages() == null || bidDoc.pages().isEmpty()) {
            return QualityCheckItemResult.fail(item, "投标文件为空，无法核查工期与商务条款响应", "请上传有效投标文件正文", Collections.emptyList());
        }

        List<QualityEvidence> evidences = new ArrayList<>();
        boolean foundAnyTerm = false;

        for (DocumentContent.PageContent page : bidDoc.pages()) {
            String text = page.text();
            if (BidLetterCheckPolicy.isCatalogOrOutlinePage(text)) {
                continue;
            }

            // 1. 占位留白硬拦截
            Matcher mPh = PLACEHOLDER_PATTERN.matcher(text);
            if (mPh.find()) {
                evidences.add(QualityEvidence.bid("商务条款未填占位", page.pageNumber(),
                        BasicInfoCheckPolicy.snippetAround(text, 150, mPh.group())));
                return QualityCheckItemResult.fail(
                        item,
                        "商务条款承诺中检测到未填写的模板占位符或空白留白！",
                        "请在投标函或商务条款响应表中明确填写具体的工期天数、质保期或有效期，严禁保留模板空白",
                        evidences);
            }

            // 2. 提取响应参数
            Integer resSched = extractScheduleDays(text);
            Integer resWar = extractWarrantyMonths(text);
            Integer resVal = extractValidityDays(text);

            if (resSched != null || resWar != null || resVal != null || text.contains("工期") || text.contains("交货期")) {
                foundAnyTerm = true;
                if (evidences.isEmpty()) {
                    evidences.add(QualityEvidence.bid("工期与商务响应页", page.pageNumber(),
                            BasicInfoCheckPolicy.snippetAround(text, 180, "工期", "交货期", "质保期", "日历天")));
                }
            }

            // 3. 工期天数超限核验
            if (requirement != null && requirement.maxScheduleDays() != null && resSched != null) {
                if (resSched > requirement.maxScheduleDays()) {
                    return QualityCheckItemResult.fail(
                            item,
                            "投标工期响应（" + resSched + "日历天）超过招标文件最高允许工期（" + requirement.maxScheduleDays()
                                    + "日历天），不满足实质性商务要求！",
                            "请核对工期承诺，修正为不超过招标文件上限天数（" + requirement.maxScheduleDays() + "日历天）",
                            evidences);
                }
            }

            // 4. 质保期缩水核验
            if (requirement != null && requirement.minWarrantyMonths() != null && resWar != null) {
                if (resWar < requirement.minWarrantyMonths()) {
                    return QualityCheckItemResult.fail(
                            item,
                            "质保期响应（" + formatMonths(resWar) + "）低于招标文件规定的最低要求（"
                                    + formatMonths(requirement.minWarrantyMonths()) + "），存在商务负偏离风险！",
                            "请修改质保期承诺，使其不少于招标文件最低要求（" + formatMonths(requirement.minWarrantyMonths()) + "）",
                            evidences);
                }
            }

            // 5. 投标有效期核验
            if (requirement != null && requirement.bidValidityDays() != null && resVal != null) {
                if (resVal < requirement.bidValidityDays()) {
                    return QualityCheckItemResult.fail(
                            item,
                            "投标有效期响应（" + resVal + "天）短于招标文件要求的最低有效期限（" + requirement.bidValidityDays() + "天）！",
                            "请在投标函中修正投标有效期承诺，满足招标文件规定期限",
                            evidences);
                }
            }

            if (!evidences.isEmpty()) {
                break;
            }
        }

        if (!foundAnyTerm) {
            return QualityCheckItemResult.fail(
                    item,
                    "未检测到明确的工期或交货期承诺条款",
                    "请在投标函或商务响应表中补充工期/交货期承诺",
                    Collections.emptyList());
        }

        String note = (requirement != null && requirement.maxScheduleDays() != null)
                ? "（满足招标文件工期上限: " + requirement.maxScheduleDays() + "日历天要求）"
                : "";
        return QualityCheckItemResult.pass(item, "已做出明确且合规的工期与商务条款响应承诺" + note, evidences);
    }

    private static Integer extractScheduleDays(final String text) {
        Matcher m = SCHEDULE_DAYS_PATTERN.matcher(text);
        if (m.find()) {
            return parseSafeInt(m.group(1));
        }
        return null;
    }

    private static Integer extractWarrantyMonths(final String text) {
        Matcher mYear = WARRANTY_YEAR_PATTERN.matcher(text);
        if (mYear.find()) {
            Integer years = parseSafeInt(mYear.group(1));
            if (years != null) {
                return years * 12;
            }
        }
        Matcher mMonth = WARRANTY_MONTH_PATTERN.matcher(text);
        if (mMonth.find()) {
            return parseSafeInt(mMonth.group(1));
        }
        return null;
    }

    private static Integer extractValidityDays(final String text) {
        Matcher m = VALIDITY_DAYS_PATTERN.matcher(text);
        if (m.find()) {
            return parseSafeInt(m.group(1));
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

    private static String formatMonths(final int months) {
        if (months % 12 == 0) {
            return (months / 12) + "年";
        }
        return months + "个月";
    }
}
