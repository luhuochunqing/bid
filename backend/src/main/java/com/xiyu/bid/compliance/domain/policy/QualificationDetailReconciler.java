// Input: 原始裁决结果（QualityCheckItemResult）+ 资质比对结构化明细（List<QualificationDetail>）
// Output: 状态与明细一致性校准后的 QualityCheckItemResult（纯核心，FP-Java 纯函数，文案人性化消除技术术语）
// Pos: compliance/domain/policy/ - 资质比对明细与总项裁决状态一致性校准器
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.QualificationDetail;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 资质比对明细与总项裁决状态一致性校准器（纯核心，spec 049 / 准则 15）.
 *
 * <p>解决双轨脱节问题：
 * <ul>
 *   <li>任何资质过期（EXPIRED）或等级不足：一票否决，强制判定 FAIL（不通过）；</li>
 *   <li>检索未命中或证明事实不完整：只能转为 MANUAL_REVIEW，不能据此认定材料缺失；</li>
 *   <li>存在需关注项（ATTENTION）：资质库未维护但标书有疑似证明，或即将过期，若原状态为 PASS，降级为 MANUAL_REVIEW（待确认）；</li>
 *   <li>全量满足（MATCHED）：保持原状态（通常为 PASS）。</li>
 * </ul>
 */
public final class QualificationDetailReconciler {

    public static QualityCheckItemResult reconcile(final QualityCheckItemResult raw,
            final List<QualificationDetail> details, final TenderRequirements requirements) {
        return reconcile(raw, details, requirements, null);
    }

    public static QualityCheckItemResult reconcile(final QualityCheckItemResult raw,
            final List<QualificationDetail> details, final TenderRequirements requirements,
            final com.xiyu.bid.compliance.domain.DocumentContent bidDoc) {
        if (raw == null) return null;
        if (!BidQualityCheckItem.QUALIFICATION_MATCH.getCode().equals(raw.itemCode())) {
            return details == null || details.isEmpty() ? raw : raw.withDetails(details);
        }
        List<QualificationDetail> entries = checkedDetails(raw, details);
        var hardDefect = QualificationMatchPolicy.checkFirstStage(bidDoc, requirements);
        if (hardDefect.isPresent() && hardDefect.orElseThrow().status() == QualityCheckStatus.FAIL)
            return hardDefect.orElseThrow().withDetails(entries);
        var result = reconcileRequirements(raw, entries, requirements);
        if (hardDefect.isPresent() && result.status() != QualityCheckStatus.FAIL)
            return hardDefect.orElseThrow().withDetails(result.details());
        return result;
    }

    private static QualityCheckItemResult reconcileRequirements(final QualityCheckItemResult raw,
            final List<QualificationDetail> entries, final TenderRequirements requirements) {
        if (entries.isEmpty() && requirements != null && !requirements.certificateRequirements().isEmpty()) {
            return QualityCheckItemResult.manualReview(BidQualityCheckItem.QUALIFICATION_MATCH,
                    "证书要求存在但未形成比对明细，不能据此判定材料齐全", "请逐项核验原始证书及投标证明", raw.evidences())
                    .withDetails(entries);
        }
        if (requirements == null || !requirements.hasUnresolvedCertificateRequirements()
                && entries.stream().noneMatch(d -> requirements.isUnresolvedCertificateRequirement(d.requirement()))) return reconcileMaterialFacts(raw, entries);
        List<QualificationDetail> references = entries.stream()
                .map(d -> !requirements.isUnresolvedCertificateRequirement(d.requirement()) ? d : new QualificationDetail(d.requirement(), d.matchedCertName(),
                        "ATTENTION", d.bidPageNumber(), d.bidQuote(), d.remainingDays(),
                        formatUnresolvedRemark(d), d.finding())).toList();
        var pending = QualityCheckItemResult.manualReview(BidQualityCheckItem.QUALIFICATION_MATCH,
                "证书条件适用范围尚未确认，资料检索不能证明资格满足或缺失", "请按原文逐项确认", raw.evidences());
        List<QualificationDetail> known = entries.stream().filter(d -> !requirements.isUnresolvedCertificateRequirement(d.requirement())).toList();
        return reconcileMaterialFacts(pending, known).withDetails(references);
    }

    private static String formatUnresolvedRemark(final QualificationDetail d) {
        String baseRemark = d.remark();
        String prefix = "条件适用范围或作用对象待确认";
        if (baseRemark.isBlank()) return prefix;
        if (baseRemark.contains("已匹配") || baseRemark.contains("未检索") || baseRemark.contains("未匹配") || baseRemark.contains("已在标书")) {
            return prefix + "；" + baseRemark;
        }
        String statusDesc = switch (d.status()) {
            case "MATCHED" -> "资质库已匹配有效证书";
            case "UNMATCHED" -> "资质库未检索到对应有效证书";
            case "EXPIRED" -> "资质库对应证书已过期";
            default -> "需人工核对";
        };
        return prefix + "；" + statusDesc + "；" + baseRemark;
    }

    private QualificationDetailReconciler() {
    }

    /** 空参数不抹掉原有事实；所有事实仍经过有效性与适用范围校准。 */
    private static List<QualificationDetail> checkedDetails(final QualityCheckItemResult raw,
            final List<QualificationDetail> supplied) {
        List<QualificationDetail> entries = (supplied == null ? List.<QualificationDetail>of() : supplied).stream()
                .filter(d -> d != null).toList();
        return (entries.isEmpty() ? raw.details() : entries).stream()
                .filter(d -> d != null).map(QualificationDetailReconciler::validateFinding).toList();
    }

    private static QualificationDetail validateFinding(final QualificationDetail detail) {
        if (detail.finding() == QualificationDetail.Finding.GRADE_INSUFFICIENT
                && (detail.matchedCertName().isBlank()
                    || QualificationGradePolicy.isGradeCompatible(detail.requirement(), detail.matchedCertName()))) {
            return new QualificationDetail(detail.requirement(), detail.matchedCertName(), "ATTENTION", detail.bidPageNumber(),
                    detail.bidQuote(), detail.remainingDays(), "等级冲突尚未得到实际比较证明；" + detail.remark(), QualificationDetail.Finding.UNKNOWN);
        }
        return detail;
    }

    /**
     * 校准资质匹配项裁决结果（纯函数）.
     *
     * @param rawResult 原始 LLM / 规则初筛裁决结果
     * @param details 资质比对结构化清单
     * @return 经明细一致性校准后的最终结果
     */
    public static QualityCheckItemResult reconcile(
            final QualityCheckItemResult rawResult,
            final List<QualificationDetail> details) {
        if (rawResult == null) return null;
        if (!BidQualityCheckItem.QUALIFICATION_MATCH.getCode().equals(rawResult.itemCode())) {
            return details == null || details.isEmpty() ? rawResult : rawResult.withDetails(details);
        }
        return reconcileMaterialFacts(rawResult, checkedDetails(rawResult, details));
    }

    private static QualityCheckItemResult reconcileMaterialFacts(final QualityCheckItemResult rawResult,
            final List<QualificationDetail> checked) {
        List<QualificationDetail> expiredList = new ArrayList<>();
        List<QualificationDetail> gradeDefects = new ArrayList<>();
        List<QualificationDetail> unproven = new ArrayList<>();
        List<QualificationDetail> attentionList = new ArrayList<>();

        for (QualificationDetail d : checked) {
            switch (d.finding()) {
                case EXPIRED -> expiredList.add(d);
                case GRADE_INSUFFICIENT -> gradeDefects.add(d);
                case LOOKUP_MISS, EVIDENCE_INCOMPLETE, UNKNOWN -> unproven.add(d);
                case MATCHED_REFERENCE -> { if ("ATTENTION".equals(d.status())) attentionList.add(d); }
            }
        }

        // 1. 存在过期资质：一票否决为 FAIL
        if (!expiredList.isEmpty()) {
            List<String> expiredCerts = expiredList.stream()
                    .map(d -> !d.matchedCertName().isBlank() ? d.matchedCertName() : d.requirement())
                    .toList();
            String conclusion = "【资质已过期】核查发现证书「" + String.join("、", expiredCerts) + "」已超出有效期，存在实质性废标风险；"
                    + cleansePassPhrasesForFail(rawResult.conclusion());
            String suggestion = "请更新或替换已过期的资质证书后重新核查";
            return new QualityCheckItemResult(
                    rawResult.itemCode(), rawResult.category(), rawResult.title(), rawResult.description(),
                    QualityCheckStatus.FAIL, rawResult.severity(), rawResult.order(),
                    conclusion, suggestion, rawResult.evidences(), checked);
        }

        // 2. 等级比较已形成明确事实；与纯检索未命中分开
        if (!gradeDefects.isEmpty()) {
            List<String> missingReqs = gradeDefects.stream()
                    .map(QualificationDetail::requirement)
                    .filter(r -> r != null && !r.isBlank())
                    .toList();
            String conclusion = "【资质等级不足】招标证书要求（" + String.join("；", missingReqs)
                    + "）与已匹配企业证书的等级不符；"
                    + cleansePassPhrasesForFail(rawResult.conclusion());
            String suggestion = "请在投标文件中完整补充对应资质证明材料或更新企业资质库";
            return new QualityCheckItemResult(
                    rawResult.itemCode(), rawResult.category(), rawResult.title(), rawResult.description(),
                    QualityCheckStatus.FAIL, rawResult.severity(), rawResult.order(),
                    conclusion, suggestion, rawResult.evidences(), checked);
        }

        if (!unproven.isEmpty() || rawResult.status() == QualityCheckStatus.FAIL) {
            List<QualificationDetail> references = checked.stream()
                    .map(d -> unproven.contains(d) ? d.pendingReview() : d).toList();
            return QualityCheckItemResult.manualReview(BidQualityCheckItem.QUALIFICATION_MATCH,
                    "[" + ManualReasons.EVIDENCE_INSUFFICIENT + "] 证明材料事实尚未确认，检索未命中或模型失败结论不能证明材料缺失",
                    "请核验原始证明及扫描附件，检索状态只作参考", rawResult.evidences()).withDetails(references);
        }

        // 3. 材料事实已确认后，单独生成需关注项的复核说明。
        if (!attentionList.isEmpty() && rawResult.status() == QualityCheckStatus.PASS) {
            return reviewAttention(rawResult, checked, attentionList);
        }
        return checked.isEmpty() ? rawResult : rawResult.withDetails(checked);
    }

    /** 显式Finding的兼容调用也可能缺少定位，保留防御性的人工复核分支。 */
    private static QualityCheckItemResult reviewAttention(final QualityCheckItemResult rawResult,
            final List<QualificationDetail> checked, final List<QualificationDetail> attentionList) {
        if (attentionList.stream().anyMatch(d -> d.bidPageNumber() == null && !d.matchedCertName().isBlank())) {
            return QualityCheckItemResult.manualReview(BidQualityCheckItem.QUALIFICATION_MATCH,
                    "资质库已检索到证书，但本次投标文件证明尚未可靠定位，不能据此判定已提供",
                    "请核对实际证明页及扫描附件；资料库记录与投标材料分别核验", rawResult.evidences()).withDetails(checked);
        }
        List<QualificationDetail> expiringList = attentionList.stream()
                .filter(d -> d.matchedCertName() != null && !d.matchedCertName().isBlank()).toList();
        List<QualificationDetail> unmaintainedList = attentionList.stream()
                .filter(d -> d.matchedCertName() == null || d.matchedCertName().isBlank()).toList();
        String conclusionPrefix;
        String suggestion;
        if (unmaintainedList.isEmpty()) {
            List<String> expiringNotices = expiringList.stream()
                    .map(d -> "证书「" + d.matchedCertName() + "」即将到期"
                            + (d.remainingDays() != null ? "（剩余 " + d.remainingDays() + " 天）" : ""))
                    .toList();
            conclusionPrefix = "[" + ManualReasons.MANUAL_REVIEW_REQUIRED + "] 资质比对提示证书临期（"
                    + String.join("；", expiringNotices) + "），请人工复核资质有效期与换证进度；";
            suggestion = "请复核即将到期的资质证书有效期并及时推进换证续期";
        } else {
            List<String> attentionReqs = attentionList.stream()
                    .map(d -> d.requirement() + (d.bidPageNumber() != null ? "（标书第" + d.bidPageNumber() + "页）" : ""))
                    .toList();
            conclusionPrefix = "[" + ManualReasons.MANUAL_REVIEW_REQUIRED + "] 资质比对存在待人工核实项（"
                    + String.join("；", attentionReqs) + "），请人工复核标书中资质证明材料的有效性；";
            suggestion = "请人工复核标书中资质证明材料与资质有效期后确认";
        }
        String conclusion = conclusionPrefix + cleansePassPhrasesForManual(rawResult.conclusion());
        return new QualityCheckItemResult(
                rawResult.itemCode(), rawResult.category(), rawResult.title(), rawResult.description(),
                QualityCheckStatus.MANUAL_REVIEW, rawResult.severity(), rawResult.order(),
                conclusion, suggestion, rawResult.evidences(), checked);
    }

    /** 清洗降级为人工复核结论中的陈述冲突短语（避免前称待核实、后称匹配通过）. */
    static String cleansePassPhrasesForManual(final String rawConclusion) {
        if (rawConclusion == null || rawConclusion.isBlank()) return "";
        return rawConclusion
                .replaceAll("，?\\s*完全满足招标文件的硬性资格要求", "，硬性证明材料已在标书中呈现，但需结合资质状态人工复核")
                .replaceAll("，?\\s*资质匹配通过[。]?", "，建议人工复核确认。")
                .replaceAll("，+", "，").replaceAll("。+", "。").replaceAll("，。", "。");
    }

    /** 清洗降级为失败结论中的通过类表述. */
    static String cleansePassPhrasesForFail(final String rawConclusion) {
        if (rawConclusion == null || rawConclusion.isBlank()) return "";
        return rawConclusion
                .replaceAll("，?\\s*完全满足招标文件的硬性资格要求", "，硬性资质未能完全满足")
                .replaceAll("，?\\s*资质匹配通过[。]?", "，存在实质性偏离或废标风险。")
                .replaceAll("，+", "，").replaceAll("。+", "。").replaceAll("，。", "。");
    }

    /** 为招标文件资质要求提取标书检索候选关键词（纯函数，剥离括号注释与长句说明）. */
    public static List<String> buildBidSearchCues(final String req) {
        if (req == null || req.isBlank()) return Collections.emptyList();
        List<String> cues = new ArrayList<>();
        String trimmed = req.trim();
        cues.add(trimmed);
        String strippedPrefix = QualificationVetoPolicy.stripQualificationPrefix(trimmed);
        if (!strippedPrefix.isBlank() && !cues.contains(strippedPrefix)) cues.add(strippedPrefix);

        // 剥离中文/英文括号及内部注释
        String strippedBrackets = trimmed.replaceAll("[（(][^）)]*[）)]", "").trim();
        if (!strippedBrackets.isBlank() && !strippedBrackets.equals(trimmed)) cues.add(strippedBrackets);
        String strippedCore = strippedPrefix.replaceAll("[（(][^）)]*[）)]", "").trim();
        if (!strippedCore.isBlank() && !cues.contains(strippedCore)) cues.add(strippedCore);

        // 按逗号/分号截取前导主体名称
        String targetForSplit = strippedBrackets.isBlank() ? trimmed : strippedBrackets;
        String[] parts = targetForSplit.split("[,，;；]");
        if (parts.length > 0 && !parts[0].isBlank() && !cues.contains(parts[0].trim())) {
            cues.add(parts[0].trim());
        }

        // 针对常见银行与主体证明材料提取关键名词
        if (trimmed.contains("基本存款账户") && !cues.contains("基本存款账户")) cues.add("基本存款账户");
        if (trimmed.contains("开户许可证") && !cues.contains("开户许可证")) cues.add("开户许可证");
        if ((trimmed.contains("开户许可证") || trimmed.contains("基本存款账户") || trimmed.contains("开户证明"))
                && !cues.contains("开户证明")) cues.add("开户证明");

        return Collections.unmodifiableList(cues);
    }
}
