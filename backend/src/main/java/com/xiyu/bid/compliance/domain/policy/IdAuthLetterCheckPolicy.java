// Input: 投标文件 DocumentContent + 可选契约 IdAuthLetterRequirement
// Output: 身份证明/身份证核查结果及定位证据（抽取到证件图像且正反面要素完整时 PASS，纯文本或要素不全保留人工）
// Pos: compliance/domain/policy/ - 标书法定代表人及委托代理人身份证明/身份证核查策略 (FP-Java)
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
 * 标书法定代表人及委托代理人身份证明/身份证附件核查策略（FP-Java纯核心，无Spring/无IO/无异常）.
 * 严格防范目录点线超前命中假通过与未填占位模板放行；抽取到证件图像且具备正反面完整要素时判定 PASS，纯文本或要素不全保留人工.
 */
public final class IdAuthLetterCheckPolicy {

    private static final Pattern MULTI_DOT_PATTERN = Pattern.compile("(?:\\.{4,}|…{2,}|·{4,})");
    private static final Pattern CATALOG_DOT_LINE_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:[第（(]?[0-9一二三四五六七八九十]+[、.)）]|(?:一|二|三|四|五|六|七|八|九|十|\\d+)[.、]).*(?:\\.{3,}|…{2,}|·{3,})\\s*\\d+\\s*$");

    private static final Pattern UNFILLED_PLACEHOLDER_PATTERN = Pattern.compile(
            "(?i)(?:【(?:此处粘贴(?:法定代表人|委托代理人|受托人)?身份证|身份证(?:正反面|正面|反面|扫描件|复印件)|身份证粘贴处)[^】]*】|"
                    + "\\[(?:此处粘贴|身份证|粘贴处)[^\\]]*\\]|"
                    + "身份证(?:号|号码)?[：:\\s]*_{3,}|"
                    + "公民身份号码[：:\\s]*_{3,})");

    /** GB 11643-1999 18位公民身份号码标准正则. */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile(
            "[1-9]\\d{5}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]");

    /** 图片证据标识正则：Markdown 图片、HTML img 标签、或转义出的 [已附图片] / 已附图片 标记. */
    static final Pattern IMAGE_EVIDENCE_PATTERN = Pattern.compile(
            "!\\[[^\\]]*\\]\\([^)]+\\)|<img[^>]+>|\\[已附图片\\]|已附图片|\\[图片[^\\]]*\\]"
    );

    private IdAuthLetterCheckPolicy() {
    }

    // REUSE-OK: * — 质检身份证明附件核查入口策略，业务域独立
    public static QualityCheckItemResult check(final DocumentContent bidDoc) {
        return check(bidDoc, IdAuthLetterRequirement.standard());
    }

    // REUSE-OK: * — 质检身份证明带契约核查入口，业务域独立
    public static QualityCheckItemResult check(
            final DocumentContent bidDoc, final IdAuthLetterRequirement requirement) {
        BidQualityCheckItem item = BidQualityCheckItem.ID_AUTH_LETTER;
        if (bidDoc == null || bidDoc.pages() == null || bidDoc.pages().isEmpty()) {
            return QualityCheckItemResult.fail(item, "未检索到投标文件正文", "请上传完整的投标文件后重新核查", Collections.emptyList());
        }

        IdAuthLetterRequirement req = requirement != null ? requirement : IdAuthLetterRequirement.standard();
        DocumentContent.PageContent catalogMatchPage = null;
        DocumentContent.PageContent placeholderMatchPage = null;

        for (DocumentContent.PageContent page : bidDoc.pages()) {
            String text = page.text();
            if (text == null || text.isBlank()) {
                continue;
            }

            boolean mentionsIdOrProof = isIdOrProofMentioned(text);
            if (!mentionsIdOrProof) {
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

            // 文本号码与图片标记只用于定位，不证明身份证正反面或授权有效
            Matcher idMatcher = ID_CARD_PATTERN.matcher(text);
            boolean hasIdNumber = idMatcher.find();
            String idCardNumber = hasIdNumber ? idMatcher.group() : null;

            boolean hasImageEvidence = IMAGE_EVIDENCE_PATTERN.matcher(text).find();
            boolean hasIdScanFeatures = hasIdScanFeatures(text);
            boolean hasLegalRepProof = text.contains("法定代表人身份证明") || text.contains("法定代表人证明书");

            if ((hasIdNumber || hasImageEvidence) && (hasIdScanFeatures || hasLegalRepProof || text.contains("身份证"))) {
                String anchor = hasIdNumber ? idCardNumber : "已附图片";
                String snippet = extractSnippet(text, anchor, 200);
                // 若招标明确要求社保证明，核查标书中是否具备社保要素
                if (req.requireSocialSecurity() && !hasSocialSecurityFeatures(bidDoc)) {
                    return QualityCheckItemResult.manualReview(item,
                            "已附身份证件凭证" + (idCardNumber != null ? "(号码: " + maskId(idCardNumber) + ")" : "")
                                    + "，但未检测到招标要求的委托代理人社保证明，需人工复核",
                            "请核实标书中是否在其他附件中提供了受托人近期的社保缴纳证明",
                            List.of(QualityEvidence.bid("身份证附件页", page.pageNumber(), snippet)));
                }

                if (!bidDoc.images().isEmpty() && hasCompleteBothSidesIdFacts(text)) {
                    return QualityCheckItemResult.pass(item,
                            "已核验法定代表人/委托代理人身份证件正反面要素（姓名、公民身份号码、签发机关及有效期限齐全）",
                            List.of(QualityEvidence.bid("身份证明/身份证正文", page.pageNumber(), snippet)));
                }
                return QualityCheckItemResult.manualReview(item,
                        "已定位身份证件文字或图片标记，尚不能证明正反面齐全、人员一致及授权签署有效",
                        "请核对原始身份证正反面、人员一致性与授权签署；号码和图片标记不能替代图像内容核验",
                        List.of(QualityEvidence.bid("身份证明/身份证正文", page.pageNumber(), snippet)));
            }
        }

        if (placeholderMatchPage != null) {
            String snippet = extractSnippet(placeholderMatchPage.text(), "身份证", 150);
            return QualityCheckItemResult.manualReview(item,
                    "检测到身份证明/身份证章节包含未填占位符模板，疑似未实际附扫描件",
                    "请按原文摘录检查对应位置是否按要求粘贴清晰有效的身份证扫描件",
                    List.of(QualityEvidence.bid("占位符模板疑似页", placeholderMatchPage.pageNumber(), snippet)));
        }

        if (catalogMatchPage != null) {
            String snippet = extractSnippet(catalogMatchPage.text(), "身份证", 150);
            return QualityCheckItemResult.manualReview(item,
                    "仅在目录页检测到身份证明/身份证索引，正文中未检索到有效身份证扫描件或号码",
                    "请核实投标文件正文中是否遗漏了法定代表人或委托代理人身份证复印件",
                    List.of(QualityEvidence.bid("目录索引", catalogMatchPage.pageNumber(), snippet)));
        }

        return QualityCheckItemResult.fail(item, "未检索到法定代表人或委托代理人身份证件证明", "请在标书附件中补充清晰有效的身份证扫描件", Collections.emptyList());
    }

    private static boolean isIdOrProofMentioned(final String text) {
        return text.contains("身份证")
                || text.contains("身份证明")
                || text.contains("居民身份证")
                || text.contains("公民身份号码");
    }

    static boolean hasCompleteBothSidesIdFacts(final String text) {
        if (text == null || !ID_CARD_PATTERN.matcher(text).find()) {
            return false;
        }
        boolean hasFaceSide = text.contains("姓名") && (text.contains("性别") || text.contains("民族") || text.contains("住址"));
        boolean hasBackSide = text.contains("签发机关") && text.contains("有效期限");
        return hasFaceSide && hasBackSide;
    }

    private static boolean hasIdScanFeatures(final String text) {
        boolean hasFaceSide = text.contains("姓名") && (text.contains("性别") || text.contains("民族")
                || text.contains("出生") || text.contains("住址") || text.contains("公民身份号码"));
        boolean hasBackSide = text.contains("中华人民共和国居民身份证")
                || text.contains("签发机关")
                || text.contains("有效期限");
        return hasFaceSide || hasBackSide || text.contains("身份证复印件") || text.contains("身份证扫描件")
                || text.contains("身份证（人像页）") || text.contains("身份证（国徽页）")
                || text.contains("人像面") || text.contains("国徽面");
    }

    private static boolean hasSocialSecurityFeatures(final DocumentContent bidDoc) {
        if (bidDoc == null || bidDoc.pages() == null) {
            return false;
        }
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            String text = page.text();
            if (text != null && (text.contains("社保证明") || text.contains("社会保险缴纳")
                    || text.contains("参保证明") || text.contains("养老保险"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCatalogOrOutlinePage(final String text, final List<String> headings) {
        if (headings != null && headings.stream().anyMatch(h -> h.contains("目录") || h.contains("目  录"))) {
            return true;
        }
        if (CATALOG_DOT_LINE_PATTERN.matcher(text).find()) {
            return true;
        }
        Matcher matcher = MULTI_DOT_PATTERN.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
            if (count >= 3) {
                return true;
            }
        }
        return false;
    }

    private static String maskId(final String id) {
        if (id == null || id.length() < 18) {
            return id;
        }
        return id.substring(0, 6) + "********" + id.substring(14);
    }

    private static String extractSnippet(final String text, final String anchor, final int radius) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int index = text.indexOf(anchor);
        if (index < 0) {
            return text.length() <= radius * 2 ? text : text.substring(0, radius * 2);
        }
        int start = Math.max(0, index - radius);
        int end = Math.min(text.length(), index + anchor.length() + radius);
        return text.substring(start, end).replace("\n", " ").trim();
    }
}
