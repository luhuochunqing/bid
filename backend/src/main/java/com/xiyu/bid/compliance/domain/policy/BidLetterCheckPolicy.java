// Input: 投标文件 DocumentContent + 可选招标投标函契约 BidLetterRequirement
// Output: 投标函格式规范核查结果 QualityCheckItemResult（PASS/FAIL/MANUAL）及证据
// Pos: compliance/domain/policy/ - 投标函格式规范与事实要素反假核查策略 (FP-Java)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 投标函/报价函格式规范纯函数核查策略 (FP-Java).
 * 零框架依赖，严格拦截目录页点线与占位符模板，落实真实要素与招标契约比对.
 */
public final class BidLetterCheckPolicy {

    record PageMatch(int pageNumber, String text) { }

    static final String[] BID_LETTER_KEYWORDS = {
            "投标函", "报价函", "响应函", "投标响应函", "谈判响应函", "磋商响应函",
            "采购响应函", "投 标 函", "报 价 函", "响 应 函", "投标承诺函", "报价承诺函", "投标申请函"
    };

    private static final Pattern SALUTATION_PATTERN = Pattern.compile(
            "(?:致[：:\\s]|（(?:采购人|招标人|采购代理|招标代理).*?）|\\((?:采购人|招标人|采购代理|招标代理).*?\\)|"
                    + "(?:采购人|招标人|采购代理|招标代理).*?[：:]|[\\u4e00-\\u9fa5]{2,30}(?:公司|集团|中心|局|处|院|指挥部|委员会|学校|医院|银行|部)\\s*(?:（[^）]+）|\\([^)]+\\))?\\s*[：:])"
    );

    private static final Pattern UNFILLED_PLACEHOLDER_PATTERN = Pattern.compile(
            "致[：:\\s]*[（\\(\\[【](?:招标人|采购人|招标代理|买方).*?[）\\)\\]】]|致[：:\\s]*_{3,}|"
                    + "总价[为：:\\s]*_{3,}|人民币[：:\\s]*_{3,}|"
                    + "[（\\(\\[【](?:投标人名称|投标人全称|投标单位).*?[）\\)\\]】]"
    );

    private static final Pattern VALIDITY_PATTERN = Pattern.compile(
            "(?:投标有效期|响应有效期|报价有效期)[^0-9\\n]{0,20}(\\d{2,3})\\s*(?:日历天|天|日)"
    );

    private static final Pattern MULTI_DOT_PATTERN = Pattern.compile("\\.{4,}|……");

    private static final Pattern CATALOG_DOT_LINE_PATTERN = Pattern.compile("[\\.·…]{3,}.*\\d+");

    private BidLetterCheckPolicy() {
    }

    // REUSE-OK: * — 质检投标函格式独立核查入口策略，业务域独立
    public static QualityCheckItemResult check(final DocumentContent bidDoc) {
        return check(bidDoc, null);
    }

    // REUSE-OK: * — 质检投标函格式独立核查入口策略，业务域独立
    public static QualityCheckItemResult check(
            final DocumentContent bidDoc, final BidLetterRequirement requirement) {
        BidQualityCheckItem item = BidQualityCheckItem.BID_LETTER_FORMAT;
        PageMatch match = findBestBidLetterPage(bidDoc);
        if (match == null) {
            return QualityCheckItemResult.fail(
                    item,
                    "未检索到投标函或报价函独立章节（已排除目录索引）",
                    "请在投标文件中添加标准格式的《投标函》或《报价函》正文章节",
                    Collections.emptyList());
        }

        String title = resolveEvidenceTitle(match.text());
        List<QualityEvidence> evidences = List.of(QualityEvidence.bid(
                title, match.pageNumber(), BasicInfoCheckPolicy.snippetAround(match.text(), 200, BID_LETTER_KEYWORDS)));

        // 1. 占位模板未填写防假拦截
        if (hasUnfilledPlaceholders(match.text())) {
            return QualityCheckItemResult.manualReview(
                    item,
                    "检测到投标函/报价函，但存在未填写的占位符模板或下划线（如【采购人名称】或下划线留白），建议复核",
                    "请确认投标函中采购人称谓、投标总价或落款信息是否已完整替换填写",
                    evidences);
        }

        boolean hasSalutation = isSalutationPresent(match.text());
        boolean hasCommitment = isCommitmentPresent(match.text());
        boolean hasSignOrSeal = isSignOrSealPresent(match.text());

        if (!hasSalutation) {
            return QualityCheckItemResult.manualReview(
                    item,
                    "已找到投标函/报价函，但未检测到标准致招标人或采购人称谓，建议复核",
                    "请确认投标函/报价函抬头致招标人或采购人称谓是否准确",
                    evidences);
        }

        // 2. 对照招标要求核验称谓与有效期
        if (requirement != null) {
            if (requirement.recipientName() != null && !requirement.recipientName().isBlank()) {
                if (!match.text().contains(requirement.recipientName())) {
                    boolean hasGenericTemplateSalutation = match.text().contains("采购人或采购代理机构")
                            || match.text().contains("采购人或招标代理机构")
                            || match.text().contains("招标人或招标代理机构");
                    if (!hasGenericTemplateSalutation) {
                        return QualityCheckItemResult.manualReview(
                                item,
                                "投标函致采购人称谓与招标文件要求的【" + requirement.recipientName() + "】不一致，建议复核",
                                "请确认投标函抬头致采购人称谓是否完全符合招标文件要求",
                                evidences);
                    }
                }
            }
            if (requirement.validityDays() != null) {
                Integer bidValidity = extractValidityDays(match.text());
                if (bidValidity != null && bidValidity < requirement.validityDays()) {
                    return QualityCheckItemResult.fail(
                            item,
                            "投标函承诺的投标有效期（" + bidValidity + "天）少于招标文件要求的（" + requirement.validityDays() + "天）",
                            "请调整投标函中的投标有效期，确保不少于招标文件要求的" + requirement.validityDays() + "天",
                            evidences);
                }
            }
        }

        // 3. 要素完备通过
        if (hasCommitment && hasSignOrSeal) {
            return QualityCheckItemResult.pass(item, "投标函/报价函格式规范，要素完备（致采购人称谓、正文承诺及落款齐全）", evidences);
        }
        return QualityCheckItemResult.pass(item, "投标函/报价函格式规范，包含致招标人/采购人称谓与承诺段落", evidences);
    }

    /** 择优挑选实质函件页：优先匹配同时具备称谓、承诺与签署的正文页，严格排除目录页（TOC）. */
    static PageMatch findBestBidLetterPage(final DocumentContent bidDoc) {
        PageMatch idealMatch = null;
        PageMatch candidateMatch = null;
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            String text = page.text();
            if (text == null || text.isBlank() || isCatalogOrOutlinePage(text)) {
                continue;
            }
            if (BasicInfoCheckPolicy.containsAnyKeyword(text, BID_LETTER_KEYWORDS)) {
                boolean salutation = isSalutationPresent(text);
                boolean commitment = isCommitmentPresent(text);
                boolean signOrSeal = isSignOrSealPresent(text);
                if (salutation && commitment && signOrSeal) {
                    return new PageMatch(page.pageNumber(), text);
                }
                if (commitment && (salutation || signOrSeal) && idealMatch == null) {
                    idealMatch = new PageMatch(page.pageNumber(), text);
                }
                if (candidateMatch == null) {
                    candidateMatch = new PageMatch(page.pageNumber(), text);
                }
            }
        }
        return idealMatch != null ? idealMatch : candidateMatch;
    }

    static boolean isCatalogOrOutlinePage(final String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        boolean hasCatalogTitle = text.contains("目录") || text.contains("目 录") || text.contains("TABLE OF CONTENTS");
        boolean hasDots = MULTI_DOT_PATTERN.matcher(text).find();
        if (hasCatalogTitle && hasDots) {
            return true;
        }
        int dotLineCount = 0;
        for (String line : text.split("\\r?\\n")) {
            if (CATALOG_DOT_LINE_PATTERN.matcher(line).find()) {
                dotLineCount++;
            }
        }
        return dotLineCount >= 2;
    }

    static boolean hasUnfilledPlaceholders(final String text) {
        return text != null && UNFILLED_PLACEHOLDER_PATTERN.matcher(text).find();
    }

    static boolean isSalutationPresent(final String text) {
        return text != null && !text.isBlank() && SALUTATION_PATTERN.matcher(text).find();
    }

    static boolean isCommitmentPresent(final String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.contains("承诺") || text.contains("我方") || text.contains("仔细研究") || text.contains("保证")
                || text.contains("遵照") || text.contains("交付") || text.contains("有效期") || text.contains("撤销");
    }

    static boolean isSignOrSealPresent(final String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.contains("投标人：") || text.contains("投标人:") || text.contains("投标人（盖章）")
                || text.contains("法定代表人") || text.contains("授权代表") || text.contains("盖章")
                || text.contains("（公章）") || text.contains("日期：") || text.contains("日期:");
    }

    static Integer extractValidityDays(final String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = VALIDITY_PATTERN.matcher(text);
        if (m.find()) {
            return DeterministicBidLetterExtractor.parseDigitsSafely(m.group(1));
        }
        return null;
    }

    private static String resolveEvidenceTitle(final String text) {
        if (text != null && (text.contains("报价函") || text.contains("报 价 函"))) return "报价函章节";
        if (text != null && (text.contains("响应函") || text.contains("响 应 函"))) return "响应函章节";
        return "投标函章节";
    }
}
