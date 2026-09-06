// Input: 投标文件 DocumentContent
// Output: QualityCheckItemResult（各项基本信息合规结果）
// Pos: compliance/domain/policy/ - 基本信息与形式规范核验策略（纯核心，集成签章完备性核验）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 基本信息类质量核查策略 (FP-Java).
 */
public final class BasicInfoCheckPolicy {

    private static final Pattern PHONE_PATTERN = Pattern.compile("(1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8})");

    private BasicInfoCheckPolicy() {
    }

    public static QualityCheckItemResult checkCoverInfo(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.COVER_INFO;
        List<DocumentContent.PageContent> pages = bidDoc.pages();
        if (pages.isEmpty()) {
            return QualityCheckItemResult.fail(item, "文档为空，未检测到封面", "请上传包含完整封面的有效投标文件", Collections.emptyList());
        }
        String text = pages.get(0).text();
        boolean hasProject = text.contains("项目") || text.contains("工程") || text.contains("招标");
        boolean hasBidder = text.contains("投标") || text.contains("单位") || text.contains("公司");
        boolean hasDate = text.matches("(?s).*(\\d{4}年|\\d{4}-|\\d{4}/).*");

        List<QualityEvidence> evidences = List.of(QualityEvidence.bid("封面第一页", pages.get(0).pageNumber(), snippet(text, 200)));
        if (hasProject && hasBidder && hasDate) {
            return QualityCheckItemResult.pass(item, "封面要素完整，包含项目名称、投标人信息与编制日期", evidences);
        }
        List<String> missing = new ArrayList<>();
        if (!hasProject) missing.add("项目名称");
        if (!hasBidder) missing.add("投标人全称");
        if (!hasDate) missing.add("日期信息");
        return QualityCheckItemResult.fail(item, "封面缺少必要关键要素: " + String.join("、", missing), "请在封面第一页补全" + String.join("、", missing), evidences);
    }

    public static QualityCheckItemResult checkBidLetterFormat(final DocumentContent bidDoc) {
        return BidLetterCheckPolicy.check(bidDoc);
    }

    public static QualityCheckItemResult checkBidLetterFormat(
            final DocumentContent bidDoc, final BidLetterRequirement requirement) {
        return BidLetterCheckPolicy.check(bidDoc, requirement);
    }

    public static QualityCheckItemResult checkAuthorizationLetter(final DocumentContent bidDoc) {
        return AuthorizationLetterCheckPolicy.check(bidDoc);
    }

    public static QualityCheckItemResult checkAuthorizationLetter(
            final DocumentContent bidDoc, final AuthorizationRequirement requirement) {
        return AuthorizationLetterCheckPolicy.check(bidDoc, requirement);
    }


    public static QualityCheckItemResult checkBusinessLicense(final DocumentContent bidDoc) {
        return BusinessLicenseCheckPolicy.check(bidDoc);
    }

    public static QualityCheckItemResult checkBusinessLicense(
            final DocumentContent bidDoc, final BusinessLicenseRequirement requirement) {
        return BusinessLicenseCheckPolicy.check(bidDoc, requirement);
    }

    public static QualityCheckItemResult checkPriceConsistency(final DocumentContent bidDoc) {
        return PriceConsistencyCheckPolicy.check(bidDoc);
    }

    public static QualityCheckItemResult checkPriceConsistency(
            final DocumentContent bidDoc, final PriceRequirement requirement) {
        return PriceConsistencyCheckPolicy.check(bidDoc, requirement);
    }


    public static QualityCheckItemResult checkCurrencyUnit(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.CURRENCY_UNIT;
        PageMatch match = findFirstPageContaining(bidDoc, "人民币", "RMB", "￥", "元", "万元");
        if (match != null) {
            return QualityCheckItemResult.pass(item, "计价货币单位规范，符合人民币法定计价标准", List.of(QualityEvidence.bid("报价计价单位", match.pageNumber(), snippetAround(match.text(), 150, "人民币", "RMB", "￥", "元", "万元"))));
        }
        return QualityCheckItemResult.manualReview(item, "未识别到明确的计价货币单位", "请确认报价表中明确标明货币单位（如：人民币/元）", Collections.emptyList());
    }

    public static QualityCheckItemResult checkScheduleResponse(final DocumentContent bidDoc, final String tenderText) {
        BusinessTermsRequirement req = tenderText != null
                ? DeterministicTermsExtractor.extract(tenderText)
                : null;
        return checkScheduleResponse(bidDoc, req);
    }

    public static QualityCheckItemResult checkScheduleResponse(
            final DocumentContent bidDoc, final BusinessTermsRequirement requirement) {
        return TermConsistencyCheckPolicy.check(bidDoc, requirement);
    }

    public static QualityCheckItemResult checkSignatureSeal(final DocumentContent bidDoc) {
        return SealIntegrityCheckPolicy.check(bidDoc);
    }

    public static QualityCheckItemResult checkContactInfo(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.CONTACT_INFO;
        PageMatch match = findFirstPageContaining(bidDoc, "联系人", "电话", "邮箱", "地址");
        if (match != null) {
            boolean hasPhone = PHONE_PATTERN.matcher(match.text()).find();
            List<QualityEvidence> evidences = List.of(QualityEvidence.bid("联系人信息", match.pageNumber(), snippetAround(match.text(), 200, "联系人", "电话", "邮箱", "地址")));
            if (hasPhone) {
                return QualityCheckItemResult.pass(item, "联系人、联系电话及通讯地址信息清晰完整", evidences);
            }
            return QualityCheckItemResult.manualReview(item, "包含联系人信息但未能识别到有效电话号码", "请核对联系人电话号码格式", evidences);
        }
        return QualityCheckItemResult.fail(item, "未检索到投标人联系人及联系方式", "请在基本信息或投标函中补充联系人、手机号与邮箱", Collections.emptyList());
    }

    record PageMatch(int pageNumber, String text) {}

    static PageMatch findFirstPageContaining(final DocumentContent bidDoc, final String... keywords) {
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            if (containsAnyKeyword(page.text(), keywords)) return new PageMatch(page.pageNumber(), page.text());
        }
        return null;
    }

    static boolean containsAnyKeyword(final String text, final String... keywords) {
        if (text == null) return false;
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    static String snippet(final String text, final int maxChars) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() <= maxChars ? clean : clean.substring(0, maxChars) + "...";
    }

    /** 围绕关键词命中位置摘录证据上下文. */
    static String snippetAround(final String text, final int maxChars, final String... keywords) {
        if (text == null) return "";
        int bestIdx = -1, bestLen = 0;
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) continue;
            int idx = text.indexOf(kw);
            if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) {
                bestIdx = idx;
                bestLen = kw.length();
            }
        }
        if (bestIdx < 0) return snippet(text, maxChars);
        int before = Math.max(0, (maxChars - bestLen) / 2);
        int start = Math.max(0, bestIdx - before);
        int end = Math.min(text.length(), start + maxChars * 2);
        return snippet(text.substring(start, end), maxChars);
    }
}
