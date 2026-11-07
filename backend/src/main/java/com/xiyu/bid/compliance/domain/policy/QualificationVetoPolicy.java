// Input: 标书文档 + 招标文件 + TenderRequirements + 知识库证据
// Output: Optional<QualityCheckItemResult>（字段内明确占位/违规分包 FAIL及原位证据，证书同页具备编号与有效期实证时 PASS，仅名称命中转人工确认）
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
 * 4. 强制证书在命中页同时具备可验证的证书编号与有效期限实证时判定 PASS，仅名称命中转人工确认；
 * 5. 其余无硬信号场景移交 LLM lane 深度语义裁决.
 */
public final class QualificationVetoPolicy {

    private static final Pattern NORMALIZE_PATTERN = Pattern.compile("[\\s\\u3000\\p{P}]");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "_{3,}|【(?:请|在此|在此处|填写|输入|插入|待填|此处|盖章|签名)[^】]{0,30}】|\\[(?:请|在此|填写|待填)[^\\]]{0,30}\\]");

    /** 资格/否决声明关联上下文关键词. */
    private static final List<String> VETO_STATEMENT_KEYWORDS = List.of(
            "一票否决", "否决投标", "废标", "合规声明", "承诺书", "资格声明", "无违法", "行贿", "失信", "分包", "转包");

    /** 实质性违规分包/转包特征正则（区别于正向合规承诺）. */
    private static final Pattern SUBCONTRACT_VIOLATION_PATTERN = Pattern.compile(
            "(?:拟将|计划将|拟向|拟委托)[^。；\\n]{0,30}(?:主体|核心|关键|主要)[^。；\\n]{0,30}(?:分包|转包|外包)|(?:拟分包给|拟转包给|拟将本项目分包)");

    private static final Pattern CERT_PREFIX = Pattern.compile(
            "^(?:(?:且|并|同时)?(?:投标人|供应商|申请人|企业|公司|我方)?(?:[（(][^）)]*[）)])?(?:应当|必须|须|应|需)?(?:具有|具备|持有|取得|通过|提供)?(?:有效的|合法有效的)?)+");

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
        if (bidDoc == null) {
            return Optional.empty();
        }

        List<EvidenceTextSlice> validPages = filterNonCatalogPages(bidDoc);
        if (validPages.isEmpty()) {
            return Optional.empty();
        }

        // 1. 占位符反假拦截：检测资格声明或否决条款响应中的未填写占位符
        Optional<QualityCheckItemResult> placeholderFail = checkPlaceholders(validPages);
        if (placeholderFail.isPresent()) {
            return placeholderFail;
        }

        // 2. 实质性违规拦截：检测标书自述违规分包/转包
        Optional<QualityCheckItemResult> violationFail = checkViolations(validPages);
        if (violationFail.isPresent()) {
            return violationFail;
        }

        // 3. 证书定位参考，不以关键词命中或未命中判定完整资格结论
        if (requirements != null && !requirements.mandatoryQualifications().isEmpty()) {
            Optional<QualityCheckItemResult> qualResult = checkCertificateReferences(
                    bidDoc, validPages, tenderDoc, requirements, kbEvidences);
            if (qualResult.isPresent()) {
                return qualResult;
            }
        }

        return Optional.empty();
    }

    private static Optional<QualityCheckItemResult> checkPlaceholders(
            final List<EvidenceTextSlice> validPages) {
        for (EvidenceTextSlice page : validPages) {
            String text = page.text();
            if (text == null || !hasAnyKeyword(text, VETO_STATEMENT_KEYWORDS)) {
                continue;
            }
            Matcher hit = findPlaceholder(text);
            String placeholder = hit == null ? null : hit.group();
            if (placeholder != null) {
                QualityEvidence evidence = QualityEvidence.bid("资格/否决声明占位留白", page.pageNumber(),
                        BasicInfoCheckPolicy.snippetAt(text, hit.start(), hit.end(), 200));
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
            final List<EvidenceTextSlice> validPages) {
        for (EvidenceTextSlice page : validPages) {
            String text = page.text();
            if (text == null) {
                continue;
            }
            Matcher matcher = SUBCONTRACT_VIOLATION_PATTERN.matcher(text);
            if (matcher.find()) {
                String snippet = matcher.group();
                QualityEvidence evidence = QualityEvidence.bid("实质性违规分包陈述", page.pageNumber(),
                        BasicInfoCheckPolicy.snippetAt(text, matcher.start(), matcher.end(), 200));
                return Optional.of(QualityCheckItemResult.fail(
                        BidQualityCheckItem.QUALIFICATION_VETO,
                        "投标文件自述存在违规分包或转包行为（" + snippet + "），触犯招投标实质性否决条款",
                        "请对照招标文件废标条款自查，主体或关键性工作严禁分包或转包",
                        List.of(evidence)));
            }
        }
        return Optional.empty();
    }

    private static Optional<QualityCheckItemResult> checkCertificateReferences(
            final DocumentContent bidDoc,
            final List<EvidenceTextSlice> validPages,
            final DocumentContent tenderDoc,
            final TenderRequirements requirements,
            final List<QualityEvidence> kbEvidences) {
        List<String> validQualifications = new ArrayList<>();
        for (String qualification : requirements.certificateRequirements()) {
            if (!CertificateRequirementPolicy.isCertificateReference(qualification)) {
                continue;
            }
            if (!validQualifications.contains(qualification)) {
                validQualifications.add(qualification);
            }
        }

        if (validQualifications.isEmpty()) {
            return Optional.empty();
        }

        List<String> satisfied = new ArrayList<>();
        List<String> unsatisfied = new ArrayList<>();
        boolean allHaveSubstantiveFacts = true;
        for (String qualification : validQualifications) {
            String cleanCert = stripQualificationPrefix(qualification);
            if (cleanCert.isBlank()) cleanCert = qualification.trim();
            String normClean = normalize(cleanCert);
            String normRaw = normalize(qualification);
            List<EvidenceTextSlice> matchedPages = validPages.stream()
                    .filter(p -> p.text() != null && (normalize(p.text()).contains(normClean)
                            || normalize(p.text()).contains(normRaw)))
                    .toList();
            if (!matchedPages.isEmpty()) {
                satisfied.add(qualification);
                if (matchedPages.stream().noneMatch(p -> SemanticEvidenceBoundary.hasSubstantiveCertificateFacts(p.text()))) {
                    allHaveSubstantiveFacts = false;
                }
            } else {
                unsatisfied.add(qualification);
                allHaveSubstantiveFacts = false;
            }
        }

        if (!unsatisfied.isEmpty()) {
            List<QualityEvidence> evidences = new ArrayList<>();
            if (tenderDoc != null) {
                evidences.add(EvidenceAssembler.locateTender(tenderDoc, "招标文件资质要求", unsatisfied));
            }
            if (kbEvidences != null) {
                evidences.addAll(kbEvidences);
            }
            String conclusion = "证书要求【" + String.join("、", validQualifications)
                    + "】中尚未定位到【" + String.join("、", unsatisfied) + "】的充分证明，文本未命中不能证明原文件缺失。";
            return Optional.of(QualityCheckItemResult.manualReview(
                    BidQualityCheckItem.QUALIFICATION_VETO,
                    conclusion,
                    "请核对条件作用对象、适用范围及扫描附件；资料库记录与本次投标证明材料分别核验",
                    evidences));
        }

        List<QualityEvidence> evidences = new ArrayList<>();
        if (tenderDoc != null) {
            evidences.add(EvidenceAssembler.locateTender(tenderDoc, "招标文件资质要求", satisfied));
        }
        if (bidDoc != null) {
            evidences.add(EvidenceAssembler.locateBidSkippingCatalog(bidDoc, "标书资质响应证明", satisfied));
        }
        if (allHaveSubstantiveFacts) {
            return Optional.of(QualityCheckItemResult.pass(
                    BidQualityCheckItem.QUALIFICATION_VETO,
                    "已核验标书正文中强制资格证书（" + String.join("、", satisfied) + "）的证书编号与有效期证明，无一票否决硬伤",
                    evidences));
        }
        String conclusion = "标书已检索到证书名称：【" + String.join("、", satisfied)
                + "】；名称命中不能证明证书有效及全部资格条件满足，请逐项核验完整资格条件。";
        return Optional.of(QualityCheckItemResult.manualReview(
                BidQualityCheckItem.QUALIFICATION_VETO,
                conclusion,
                "请对照招标资格审查清单核验主体、财务、信用、业绩、纳税社保及相关证明；资质库命中不能替代本次投标证明材料核验",
                evidences));
    }

    static String stripQualificationPrefix(final String text) {
        return text == null ? "" : CERT_PREFIX.matcher(text.trim()).replaceFirst("").trim();
    }

    private static List<EvidenceTextSlice> filterNonCatalogPages(final DocumentContent doc) {
        return EvidenceTextSlice.from(doc).stream()
                .filter(p -> !p.isCatalogPage())
                .toList();
    }

    private static Matcher findPlaceholder(final String text) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            int lineStart = text.lastIndexOf('\n', matcher.start()) + 1;
            int fieldStart = lineStart;
            for (char separator : new char[]{'。', '；', ';', '，', ','}) {
                fieldStart = Math.max(fieldStart, text.lastIndexOf(separator, matcher.start()) + 1);
            }
            String field = text.substring(fieldStart, matcher.end());
            if (text.substring(lineStart, matcher.start()).isBlank() && lineStart > 0) {
                String[] lines = text.substring(0, lineStart).stripTrailing().split("\\R");
                String previous = lines.length == 0 ? "" : lines[lines.length - 1].strip();
                if (previous.length() <= 40 && hasAnyKeyword(previous, VETO_STATEMENT_KEYWORDS)) field = previous + field;
            }
            if (hasAnyKeyword(field, VETO_STATEMENT_KEYWORDS) || field.contains("资格条件")) return matcher;
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
