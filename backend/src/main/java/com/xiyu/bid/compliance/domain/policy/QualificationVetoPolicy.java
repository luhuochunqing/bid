// Input: 标书文档 + 招标文件 + TenderRequirements + 知识库证据
// Output: Optional<QualityCheckItemResult>（第一阶段硬信号：占位符反假/违规分包/硬性资质缺失直接 FAIL，否则 empty 移交 LLM）
// Pos: compliance/domain/policy/ - 标书一票否决与资格前置反假核验策略（纯核心，FP-Java 无外部依赖）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标书一票否决与资格前置反假核验策略（纯核心）.
 *
 * <p>基于中国招投标法律法规（《招标投标法》第四十八条等）与招标文件否决条款：
 * 1. 物理剔除目录点线页，杜绝目录欺骗；
 * 2. 占位符反假拦截：若资格声明或否决条款响应存在未填占位模板或空白留白，直接判定 FAIL；
 * 3. 严格区分正向合规承诺与实质性违规分包/转包，对实质性违规硬阻断 FAIL，对合规承诺放行；
 * 4. 专用行业资质证书物理缺失核验；
 * 5. 其余无硬信号场景移交 LLM lane 深度语义裁决.
 */
public final class QualificationVetoPolicy {

    private static final Pattern NORMALIZE_PATTERN = Pattern.compile("[\\s\\u3000\\p{P}]");
    private static final Pattern UNDERLINE_BLANK_PATTERN = Pattern.compile("_{3,}");
    private static final Pattern BRACKET_PLACEHOLDER_PATTERN = Pattern.compile(
            "【(?:请|在此|在此处|填写|输入|插入|待填|此处|盖章|签名)[^】]{0,30}】|\\[(?:请|在此|填写|待填)[^\\]]{0,30}\\]");

    /** 资格/否决声明关联上下文关键词. */
    private static final List<String> VETO_STATEMENT_KEYWORDS = List.of(
            "一票否决", "否决投标", "废标", "合规声明", "承诺书", "资格声明", "无违法", "行贿", "失信", "分包", "转包");

    /** 实质性违规分包/转包特征正则（区别于正向合规承诺）. */
    private static final Pattern SUBCONTRACT_VIOLATION_PATTERN = Pattern.compile(
            "(?:拟将|计划将|拟向|拟委托)[^。；\\n]{0,30}(?:主体|核心|关键|主要)[^。；\\n]{0,30}(?:分包|转包|外包)|(?:拟分包给|拟转包给|拟将本项目分包)");

    private QualificationVetoPolicy() {
    }

    /**
     * 标书一票否决与资格前置第一阶段确定性核查.
     */
    // REUSE-OK: * — 质检一票否决核查入口，业务域独立
    public static Optional<QualityCheckItemResult> checkFirstStage(
            final DocumentContent bidDoc,
            final DocumentContent tenderDoc,
            final TenderRequirements requirements,
            final List<QualityEvidence> kbEvidences) {
        if (bidDoc == null || bidDoc.fullText() == null || bidDoc.fullText().isBlank()) {
            return Optional.empty();
        }

        List<DocumentContent.PageContent> validPages = filterNonCatalogPages(bidDoc);
        if (validPages.isEmpty()) {
            return Optional.empty();
        }

        // 1. 占位符反假拦截：检测资格声明或否决条款响应中的未填写占位符
        Optional<QualityCheckItemResult> placeholderFail = checkPlaceholders(bidDoc, validPages);
        if (placeholderFail.isPresent()) {
            return placeholderFail;
        }

        // 2. 实质性违规拦截：检测标书自述违规分包/转包
        Optional<QualityCheckItemResult> violationFail = checkViolations(bidDoc, validPages);
        if (violationFail.isPresent()) {
            return violationFail;
        }

        // 3. 硬性资质证书物理正文缺失拦截
        if (requirements != null && !requirements.mandatoryQualifications().isEmpty()) {
            Optional<QualityCheckItemResult> qualFail = checkMandatoryQualifications(
                    validPages, tenderDoc, requirements, kbEvidences);
            if (qualFail.isPresent()) {
                return qualFail;
            }
        }

        return Optional.empty();
    }

    private static Optional<QualityCheckItemResult> checkPlaceholders(
            final DocumentContent bidDoc, final List<DocumentContent.PageContent> validPages) {
        for (DocumentContent.PageContent page : validPages) {
            String text = page.text();
            if (text == null || !hasAnyKeyword(text, VETO_STATEMENT_KEYWORDS)) {
                continue;
            }
            String placeholder = findPlaceholder(text);
            if (placeholder != null) {
                QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "资格/否决声明占位留白", List.of(placeholder));
                return Optional.of(QualityCheckItemResult.fail(
                        BidQualityCheckItem.QUALIFICATION_VETO,
                        "一票否决或资格前置承诺中存在未填写的占位模板或空白留白（" + placeholder + "）",
                        "请在标书中完整填写各项资格声明与合规承诺并签字盖章",
                        List.of(evidence)));
            }
        }
        return Optional.empty();
    }

    private static Optional<QualityCheckItemResult> checkViolations(
            final DocumentContent bidDoc, final List<DocumentContent.PageContent> validPages) {
        for (DocumentContent.PageContent page : validPages) {
            String text = page.text();
            if (text == null) {
                continue;
            }
            Matcher matcher = SUBCONTRACT_VIOLATION_PATTERN.matcher(text);
            if (matcher.find()) {
                String snippet = matcher.group();
                QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "实质性违规分包陈述", List.of(snippet));
                return Optional.of(QualityCheckItemResult.fail(
                        BidQualityCheckItem.QUALIFICATION_VETO,
                        "投标文件自述存在违规分包或转包行为（" + snippet + "），触犯招投标实质性否决条款",
                        "请对照招标文件废标条款自查，主体或关键性工作严禁分包或转包",
                        List.of(evidence)));
            }
        }
        return Optional.empty();
    }

    private static Optional<QualityCheckItemResult> checkMandatoryQualifications(
            final List<DocumentContent.PageContent> validPages,
            final DocumentContent tenderDoc,
            final TenderRequirements requirements,
            final List<QualityEvidence> kbEvidences) {
        StringBuilder fullValidText = new StringBuilder();
        for (DocumentContent.PageContent p : validPages) {
            if (p.text() != null) {
                fullValidText.append(p.text()).append('\n');
            }
        }
        String normalizedValidText = normalize(fullValidText.toString());

        List<QualityEvidence> evidences = new ArrayList<>();
        for (String qualification : requirements.mandatoryQualifications()) {
            if (qualification.isBlank()) {
                continue;
            }
            if (tenderDoc != null) {
                evidences.add(EvidenceAssembler.locateTender(tenderDoc, "招标文件资质要求", List.of(qualification)));
            }
            if (!normalizedValidText.contains(normalize(qualification))) {
                if (kbEvidences != null) {
                    evidences.addAll(kbEvidences);
                }
                return Optional.of(QualityCheckItemResult.fail(
                        BidQualityCheckItem.QUALIFICATION_VETO,
                        "未满足招标文件硬性要求（一票否决项）：" + qualification,
                        "请在投标文件中补充对应证明材料或在企业知识库中维护有效记录",
                        evidences));
            }
        }
        return Optional.empty();
    }

    private static List<DocumentContent.PageContent> filterNonCatalogPages(final DocumentContent doc) {
        if (doc.pages() == null || doc.pages().isEmpty()) {
            if (doc.fullText() != null && !doc.fullText().isBlank()) {
                return List.of(new DocumentContent.PageContent(1, doc.fullText(), List.of()));
            }
            return List.of();
        }
        return doc.pages().stream()
                .filter(p -> p.text() != null && !BidLetterCheckPolicy.isCatalogOrOutlinePage(p.text()))
                .toList();
    }

    private static String findPlaceholder(final String text) {
        Matcher m1 = UNDERLINE_BLANK_PATTERN.matcher(text);
        if (m1.find()) {
            return m1.group();
        }
        Matcher m2 = BRACKET_PLACEHOLDER_PATTERN.matcher(text);
        if (m2.find()) {
            return m2.group();
        }
        return null;
    }

    private static boolean hasAnyKeyword(final String text, final List<String> keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(final String text) {
        return text == null ? "" : NORMALIZE_PATTERN.matcher(text).replaceAll("");
    }
}
