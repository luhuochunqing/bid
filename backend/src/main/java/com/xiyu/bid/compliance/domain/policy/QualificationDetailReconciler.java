// Input: 原始裁决结果（QualityCheckItemResult）+ 资质比对结构化明细（List<QualificationDetail>）
// Output: 状态与明细一致性校准后的 QualityCheckItemResult（纯核心，FP-Java 纯函数）
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
 *   <li>存在未匹配项（UNMATCHED）：在资质库与标书均未检索到有效证明，存在废标风险，绝不得放行 PASS，强制降级为 FAIL；</li>
 *   <li>存在需关注项（ATTENTION）：资质库未维护但标书有疑似证明，或即将过期，若原状态为 PASS，降级为 MANUAL_REVIEW（待确认）；</li>
 *   <li>全量满足（MATCHED）：保持原状态（通常为 PASS）。</li>
 * </ul>
 */
public final class QualificationDetailReconciler {

    private QualificationDetailReconciler() {
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
        if (rawResult == null) {
            return null;
        }
        if (details == null || details.isEmpty()) {
            return rawResult;
        }
        if (!BidQualityCheckItem.QUALIFICATION_MATCH.getCode().equals(rawResult.itemCode())) {
            return rawResult.withDetails(details);
        }

        List<QualificationDetail> expiredList = new ArrayList<>();
        List<QualificationDetail> unmatchedList = new ArrayList<>();
        List<QualificationDetail> attentionList = new ArrayList<>();

        for (QualificationDetail d : details) {
            if (d == null) {
                continue;
            }
            String status = d.status() != null ? d.status().toUpperCase() : "UNMATCHED";
            switch (status) {
                case "EXPIRED" -> expiredList.add(d);
                case "UNMATCHED" -> unmatchedList.add(d);
                case "ATTENTION" -> attentionList.add(d);
                default -> { }
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
                    conclusion, suggestion, rawResult.evidences(), details);
        }

        // 2. 存在未匹配硬性资质：资质库与标书均缺失，存在废标风险，强制为 FAIL
        if (!unmatchedList.isEmpty()) {
            List<String> missingReqs = unmatchedList.stream()
                    .map(QualificationDetail::requirement)
                    .filter(r -> r != null && !r.isBlank())
                    .toList();
            String conclusion = "【资质未匹配】招标文件要求的硬性资质（" + String.join("；", missingReqs)
                    + "）在企业资质库及投标文件中均未检索到对应证明材料，存在实质性偏离或废标风险；"
                    + cleansePassPhrasesForFail(rawResult.conclusion());
            String suggestion = "请在投标文件中完整补充对应资质证明材料或更新企业资质库";
            return new QualityCheckItemResult(
                    rawResult.itemCode(), rawResult.category(), rawResult.title(), rawResult.description(),
                    QualityCheckStatus.FAIL, rawResult.severity(), rawResult.order(),
                    conclusion, suggestion, rawResult.evidences(), details);
        }

        // 缺证断言与全量已匹配事实冲突时转人工；不覆盖占位、主体不符等其他真实失败。
        if (rawResult.status() == QualityCheckStatus.FAIL
                && details.stream().allMatch(d -> d != null && d.matchedCertName() != null && !d.matchedCertName().isBlank())
                && rawResult.conclusion().matches("(?s).*(?:未.*匹配|未.*录入|缺少.*证书|没有.*证书).*")
                && !rawResult.conclusion().matches("(?s).*(?:占位|留白|伪造|主体.*不一|等级不足).*")) {
            return new QualityCheckItemResult(rawResult.itemCode(), rawResult.category(), rawResult.title(), rawResult.description(),
                    QualityCheckStatus.MANUAL_REVIEW, rawResult.severity(), rawResult.order(),
                    "[" + ManualReasons.MANUAL_REVIEW_REQUIRED + "] 原资质缺失结论与企业资质库匹配明细不一致，请人工核对证书正文及有效期",
                    "请对照逐项匹配明细核对证书，不得仅依据冲突的缺失结论判定不合格", rawResult.evidences(), details);
        }

        // 3. 存在需关注项（ATTENTION）：区分仅临期 vs 资质库未维护
        if (!attentionList.isEmpty() && rawResult.status() == QualityCheckStatus.PASS) {
            List<QualificationDetail> expiringList = attentionList.stream()
                    .filter(d -> d.matchedCertName() != null && !d.matchedCertName().isBlank())
                    .toList();
            List<QualificationDetail> unmaintainedList = attentionList.stream()
                    .filter(d -> d.matchedCertName() == null || d.matchedCertName().isBlank())
                    .toList();

            String conclusionPrefix;
            String suggestion;
            if (unmaintainedList.isEmpty()) {
                // 纯证书临期预警，标书材料本身齐全
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
                    conclusion, suggestion, rawResult.evidences(), details);
        }

        // 4. 全量 MATCHED 或非 PASS 原始状态保持
        return rawResult.withDetails(details);
    }

    /**
     * 清洗降级为人工复核结论中的陈述冲突短语（避免前称待核实、后称匹配通过）.
     */
    static String cleansePassPhrasesForManual(final String rawConclusion) {
        if (rawConclusion == null || rawConclusion.isBlank()) {
            return "";
        }
        String text = rawConclusion
                .replaceAll("，?\\s*完全满足招标文件的硬性资格要求", "，硬性证明材料已在标书中呈现，但需结合资质状态人工复核")
                .replaceAll("，?\\s*资质匹配通过[。]?", "，建议人工复核确认。");
        return text.replaceAll("，+", "，").replaceAll("。+", "。").replaceAll("，。", "。");
    }

    /**
     * 清洗降级为失败结论中的通过类表述.
     */
    static String cleansePassPhrasesForFail(final String rawConclusion) {
        if (rawConclusion == null || rawConclusion.isBlank()) {
            return "";
        }
        String text = rawConclusion
                .replaceAll("，?\\s*完全满足招标文件的硬性资格要求", "，硬性资质未能完全满足")
                .replaceAll("，?\\s*资质匹配通过[。]?", "，存在实质性偏离或废标风险。");
        return text.replaceAll("，+", "，").replaceAll("。+", "。").replaceAll("，。", "。");
    }

    /**
     * 为招标文件资质要求提取标书检索候选关键词（纯函数，剥离括号注释与长句说明）.
     */
    public static List<String> buildBidSearchCues(final String req) {
        if (req == null || req.isBlank()) {
            return Collections.emptyList();
        }
        List<String> cues = new ArrayList<>();
        String trimmed = req.trim();
        cues.add(trimmed);

        // 剥离中文/英文括号及内部注释
        String strippedBrackets = trimmed.replaceAll("[（(][^）)]*[）)]", "").trim();
        if (!strippedBrackets.isBlank() && !strippedBrackets.equals(trimmed)) {
            cues.add(strippedBrackets);
        }

        // 按逗号/分号截取前导主体名称
        String targetForSplit = strippedBrackets.isBlank() ? trimmed : strippedBrackets;
        String[] parts = targetForSplit.split("[,，;；]");
        if (parts.length > 0 && !parts[0].isBlank()) {
            String firstPart = parts[0].trim();
            if (!cues.contains(firstPart)) {
                cues.add(firstPart);
            }
        }

        // 针对常见银行与主体证明材料提取关键名词
        if (trimmed.contains("基本存款账户") && !cues.contains("基本存款账户")) {
            cues.add("基本存款账户");
        }
        if (trimmed.contains("开户许可证") && !cues.contains("开户许可证")) {
            cues.add("开户许可证");
        }
        if ((trimmed.contains("开户许可证") || trimmed.contains("基本存款账户") || trimmed.contains("开户证明"))
                && !cues.contains("开户证明")) {
            cues.add("开户证明");
        }

        return Collections.unmodifiableList(cues);
    }
}
