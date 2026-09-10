// Input: 投标文件 DocumentContent + 可选契约 BusinessLicenseRequirement
// Output: 营业执照核查结果 QualityCheckItemResult（PASS/FAIL/MANUAL）及证据
// Pos: compliance/domain/policy/ - 标书营业执照与主体资格证明核查策略 (FP-Java)
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
 * 标书营业执照与主体资格证明核查策略（FP-Java纯核心，无Spring/无IO/无异常）.
 * 严格防范目录点线超前命中假通过与未填占位模板放行，支持已粘贴图片凭据放行.
 */
public final class BusinessLicenseCheckPolicy {

    private static final Pattern MULTI_DOT_PATTERN = Pattern.compile("(?:\\.{4,}|…{2,}|·{4,})");
    private static final Pattern CATALOG_DOT_LINE_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:[第（(]?[0-9一二三四五六七八九十]+[、.)）]|(?:一|二|三|四|五|六|七|八|九|十|\\d+)[.、]).*(?:\\.{3,}|…{2,}|·{3,})\\s*\\d+\\s*$");

    private static final Pattern UNFILLED_PLACEHOLDER_PATTERN = Pattern.compile(
            "(?i)(?:【(?:统一社会信用代码|(?:请|此处)?粘贴营业执照|营业执照(?:副本)?(?:复印件|扫描件)|法定代表人|公司名称)[^】]*】|\\[(?:统一社会信用代码|营业执照)[^\\]]*\\]|统一社会信用代码[：:\\s]*_{3,}|91[0-9A-Z]{6}\\s*X{6,})");

    /** GB 32100-2015 18位统一社会信用代码标准正则（剔除易混淆字母 I, O, Z, S, V）. */
    private static final Pattern USCC_PATTERN = Pattern.compile(
            "[0-9A-HJ-NPQRTUWXY]{2}\\d{6}[0-9A-HJ-NPQRTUWXY]{10}");

    /** 图片证据标识正则：Markdown 图片、HTML img 标签、或转义出的 [已附图片] / 已附图片 标记. */
    static final Pattern IMAGE_EVIDENCE_PATTERN = Pattern.compile(
            "!\\[[^\\]]*\\]\\([^)]+\\)|<img[^>]+>|\\[已附图片\\]|已附图片|\\[图片[^\\]]*\\]"
    );

    private BusinessLicenseCheckPolicy() {
    }

    // REUSE-OK: * — 质检营业执照核查入口，业务域独立
    public static QualityCheckItemResult check(final DocumentContent bidDoc) {
        return check(bidDoc, BusinessLicenseRequirement.standard());
    }

    // REUSE-OK: * — 质检营业执照带契约核查入口，业务域独立
    public static QualityCheckItemResult check(
            final DocumentContent bidDoc, final BusinessLicenseRequirement requirement) {
        BidQualityCheckItem item = BidQualityCheckItem.BUSINESS_LICENSE;
        if (bidDoc == null || bidDoc.pages() == null || bidDoc.pages().isEmpty()) {
            return QualityCheckItemResult.fail(item, "未检索到投标文件正文", "请上传完整的投标文件后重新核查", Collections.emptyList());
        }
        BusinessLicenseRequirement effective = requirement != null ? requirement : BusinessLicenseRequirement.standard();
        if (!effective.required()) {
            return QualityCheckItemResult.pass(item, "招标文件未要求提供营业执照或主体资格证明，本项直接通过",
                    Collections.emptyList());
        }
        List<String> allowedCerts = effective.allowCertificates() == null
                ? List.of() : effective.allowCertificates();

        DocumentContent.PageContent catalogMatchPage = null;
        DocumentContent.PageContent placeholderMatchPage = null;

        for (DocumentContent.PageContent page : bidDoc.pages()) {
            String text = page.text();
            if (text == null || text.isBlank()) {
                continue;
            }

            boolean mentionsLicense = isLicenseOrCertMentioned(text);
            if (!mentionsLicense) {
                continue;
            }

            if (isCatalogOrOutlinePage(text, page.headings())) {
                if (catalogMatchPage == null) {
                    catalogMatchPage = page;
                }
                continue;
            }

            if (UNFILLED_PLACEHOLDER_PATTERN.matcher(text).find()) {
                if (placeholderMatchPage == null) {
                    placeholderMatchPage = page;
                }
                continue;
            }

            // 正向事实要素检验：必须具备统一社会信用代码、同页证照关键词以及法律主体登记要素
            Matcher usccMatcher = USCC_PATTERN.matcher(text);
            boolean hasUscc = usccMatcher.find();
            String usccCode = hasUscc ? usccMatcher.group() : null;

            boolean hasRegistrationFacts = hasRegistrationFacts(text);
            boolean hasAuthorityOrSeal = text.contains("登记机关") || text.contains("市场监督")
                    || text.contains("行政审批") || text.contains("公章") || text.contains("盖章");
            // 同页必须出现证照本体关键词：仅提统一社会信用代码的业绩合同/往来函件页不得直通
            boolean hasLicenseKeyword = text.contains("营业执照") || text.contains("法人证书")
                    || text.contains("登记证书") || text.contains("执业许可证");
            String matchedCert = matchAllowedCertificate(text, allowedCerts);

            boolean hasImageEvidence = IMAGE_EVIDENCE_PATTERN.matcher(text).find();

            if (hasLicenseKeyword && (allowedCerts.isEmpty() || matchedCert != null)) {
                String docType = matchedCert != null ? matchedCert
                        : (text.contains("事业单位法人证书") ? "事业单位法人证书" : "营业执照");
                if (hasUscc && (hasRegistrationFacts || hasAuthorityOrSeal)) {
                    String snippet = extractSnippet(text, usccCode != null ? usccCode : docType, 200);
                    String conclusion = "已包含" + docType + "且识别到有效统一社会信用代码: " + usccCode + "，主体资格要素齐备";
                    return QualityCheckItemResult.pass(item, conclusion,
                            List.of(QualityEvidence.bid(docType + "正文", page.pageNumber(), snippet)));
                }
                if (hasImageEvidence) {
                    String snippet = extractSnippet(text, docType, 200);
                    String conclusion = "已包含" + docType + "扫描件图片凭证，主体资格证明齐备";
                    return QualityCheckItemResult.pass(item, conclusion,
                            List.of(QualityEvidence.bid(docType + "正文", page.pageNumber(), snippet)));
                }
            }

            // 事业单位法人证书或未提取到标准USCC但包含明确法人登记号的特例
            if (text.contains("事业单位法人证书") && (allowedCerts.isEmpty() || allowedCerts.contains("事业单位法人证书"))
                    && (text.contains("事证第") || text.contains("统一社会信用代码") || hasRegistrationFacts)) {
                String snippet = extractSnippet(text, "事业单位法人证书", 200);
                return QualityCheckItemResult.pass(item, "已包含事业单位法人证书，符合主体资格要求",
                        List.of(QualityEvidence.bid("事业单位法人证书", page.pageNumber(), snippet)));
            }
        }

        if (placeholderMatchPage != null) {
            String snippet = extractSnippet(placeholderMatchPage.text(), "营业执照", 150);
            return QualityCheckItemResult.manualReview(item,
                    "检测到营业执照占位模板或未填写占位符，疑似未实际附有效证照复印件",
                    "请核实是否已替换模板占位符并粘贴真实有效的营业执照扫描件",
                    List.of(QualityEvidence.bid("营业执照占位页", placeholderMatchPage.pageNumber(), snippet)));
        }

        if (catalogMatchPage != null) {
            String snippet = extractSnippet(catalogMatchPage.text(), "营业执照", 150);
            return QualityCheckItemResult.manualReview(item,
                    "仅在目录页检测到营业执照索引，正文中未检索到有效证照扫描件或统一社会信用代码",
                    "请确认投标文件商务资质章节中是否已装订清晰有效的营业执照扫描件",
                    List.of(QualityEvidence.bid("目录页", catalogMatchPage.pageNumber(), snippet)));
        }

        return QualityCheckItemResult.fail(item, "未检索到营业执照或主体资格证明文件", "请在商务资质分卷中补充营业执照清晰扫描件", Collections.emptyList());
    }

    private static boolean isLicenseOrCertMentioned(final String text) {
        return text.contains("营业执照") || text.contains("统一社会信用代码")
                || text.contains("事业单位法人证书") || text.contains("社会团体法人登记证书")
                || text.contains("民办非企业单位登记证书") || text.contains("执业许可证");
    }

    private static boolean hasRegistrationFacts(final String text) {
        int count = 0;
        if (text.contains("法定代表人") || text.contains("负责人")) count++;
        if (text.contains("注册资本") || text.contains("开办资金")) count++;
        if (text.contains("经营范围") || text.contains("业务范围")) count++;
        if (text.contains("营业期限") || text.contains("有效期限") || text.contains("有效期至")
                || text.contains("长期有效") || text.contains("至长期")) count++;
        if (text.contains("住所") || text.contains("营业场所") || text.contains("注册地址")) count++;
        return count >= 2;
    }

    /**
     * 在招标允许证照清单中匹配本页实际出现的证照类型；清单为空表示不过滤.
     * 返回命中的证照名，未命中返回 null（调用方决定跳过本页直通）.
     */
    private static String matchAllowedCertificate(final String text, final List<String> allowedCerts) {
        if (allowedCerts == null || allowedCerts.isEmpty()) {
            return null;
        }
        for (String cert : allowedCerts) {
            if (cert != null && !cert.isBlank() && text.contains(cert)) {
                return cert;
            }
        }
        return null;
    }

    private static boolean isCatalogOrOutlinePage(final String text, final List<String> headings) {
        if (headings != null) {
            for (String h : headings) {
                if (h.contains("目录") || h.contains("目  录") || h.equalsIgnoreCase("CONTENTS")) {
                    return true;
                }
            }
        }
        if (CATALOG_DOT_LINE_PATTERN.matcher(text).find()) {
            return true;
        }
        // 点线散点仅为弱信号：含统一社会信用代码的页面视为实质内容页，不判目录，
        // 避免真实证照页因两处省略号被降级为 MANUAL（目录点线行已有上式强判据覆盖）
        if (USCC_PATTERN.matcher(text).find()) {
            return false;
        }
        Matcher dotMatcher = MULTI_DOT_PATTERN.matcher(text);
        int dotCount = 0;
        while (dotMatcher.find()) {
            dotCount++;
            if (dotCount >= 2) {
                return true;
            }
        }
        return false;
    }

    private static String extractSnippet(final String text, final String anchor, final int maxLen) {
        int idx = text.indexOf(anchor);
        if (idx < 0) {
            return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
        }
        int start = Math.max(0, idx - 40);
        int end = Math.min(text.length(), idx + anchor.length() + maxLen);
        String s = text.substring(start, end).replace("\n", " ").trim();
        return (start > 0 ? "..." : "") + s + (end < text.length() ? "..." : "");
    }
}
