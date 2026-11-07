// Input: 偏离表判定与原始招投标文档
// Output: 确定性事实缺陷保留；检查未定位或要求未绑定原文转人工，只在响应性入口校准一次
// Pos: compliance/domain/policy/ - 偏离表要求来源核对纯核心
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 只确认完整原文条款，不从全文短句、参考材料或数值前缀推断本项目义务。 */
final class ResponsivenessRequirementEvidencePolicy {
    private record Clause(String id, String body) { }
    private static final Pattern NUMBERED = Pattern.compile("^(?:第([0-9]+(?:\\.[0-9]+)*)条|([0-9]+(?:\\.[0-9]+)*)(?:\\.(?![0-9])|[、)）]|\\s+))\\s*(.*)$");
    private static final Pattern REFERENCE = Pattern.compile("参考|历史|示例|样本|范例|模板");
    private ResponsivenessRequirementEvidencePolicy() { }

    static QualityCheckItemResult reconcile(QualityCheckItemResult result, DocumentContent bid, DocumentContent tender) {
        if (result == null) return unknown(null, "未定位到可验证的实际响应事实，不能由模型单独证明满足或缺失");
        // 输入仅来自确定性响应性检查；原位缺陷已经核验，不能再次用未过滤原文重跑并覆盖。
        if (!BidQualityCheckItem.RESPONSIVENESS_REVIEW.getCode().equals(result.itemCode())
                || result.status() != QualityCheckStatus.PASS) return result;
        if (tender == null || tender.fullText().isBlank()) return unknown(result, "招标原文不可用，投标人填写的要求及无偏离声明不能单独证明完整响应");
        var clauses = clauses(tender.fullText());
        var tables = DeviationTableExtractor.extract(bid).tables();
        if (tables.isEmpty()) {
            boolean tenderRequiresDeviation = tender.fullText().contains("偏离表")
                    || tender.fullText().contains("差异表")
                    || tender.fullText().contains("偏差表");
            return tenderRequiresDeviation ? result : unknown(result, "尚未定位到可与招标原文逐行核对的偏离表要求，不能仅凭响应声明判定通过");
        }
        for (var table : tables) {
            for (var row : table.rows()) {
                String requirement = compact(row.tenderRequirement());
                String identity = compact(row.clause());
                String numericId = identity.replaceAll("[第条()（）]", "");
                boolean numbered = numericId.matches("[0-9]+(?:\\.[0-9]+)*");
                boolean named = !numbered && identity.matches(".*[\\p{IsHan}a-zA-Z].*");
                String quote = named && !requirement.startsWith(identity) ? identity + requirement : requirement;
                var candidates = clauses.stream().filter(c -> numbered ? c.id().equals(numericId)
                        : named && c.body().contains(identity)).toList();
                if (requirement.isBlank() || candidates.size() != 1 || !candidates.getFirst().body().equals(quote)) {
                    return ManualReasons.manual(BidQualityCheckItem.RESPONSIVENESS_REVIEW, ManualReasons.AMBIGUOUS,
                            "偏离表中填写的招标要求尚未绑定到同一编号或明确对象的完整招标原文条款，不能仅凭无偏离声明判定通过",
                            "请核对本项目真实条款、完整数值和适用对象；参考材料及其他条款不能替代",
                            List.of(QualityEvidence.bid("待核对的偏离表行", table.pageNumber(), row.rawLine())));
                }
            }
        }
        return result;
    }

    /** 换行可能只是排版折行，不能把1年换行6个月截成两条要求。 */
    private static List<Clause> clauses(String text) {
        List<Clause> result = new ArrayList<>();
        boolean referenceScope = false;
        for (String raw : text.split("[。；;]+|(?=第[0-9]+(?:\\.[0-9]+)*条)|(?m)(?=^\\h*[0-9]+(?:\\.[0-9]+)*[、.)）])")) {
            String sentence = raw.strip();
            if (REFERENCE.matcher(sentence).find()) { referenceScope = true; continue; }
            if (sentence.matches("^(?:本项目|本次采购|本次招标|招标要求|采购要求).*")) referenceScope = false;
            if (referenceScope || sentence.isBlank()) continue;
            var numbered = NUMBERED.matcher(sentence);
            String id = "";
            if (numbered.matches()) {
                id = numbered.group(1) == null ? numbered.group(2) : numbered.group(1);
                sentence = numbered.group(3);
            }
            String body = compact(sentence).replaceFirst("^(?:本项目|本次采购|本次招标|真实招标要求|招标要求|采购要求)", "")
                    .replaceAll("^[，,、]+|[，,、]+$", "");
            result.add(new Clause(id, body));
        }
        return result;
    }

    private static QualityCheckItemResult unknown(QualityCheckItemResult result, String reason) {
        return ManualReasons.manual(BidQualityCheckItem.RESPONSIVENESS_REVIEW, ManualReasons.AMBIGUOUS, reason,
                "请取得招标原文后按完整条款逐行核对偏离表", result == null ? List.of() : result.evidences());
    }

    private static String compact(String text) { return text == null ? "" : text.replaceAll("[\\s：:]+", ""); }
}
