// Input: 规则/LLM裁决结果与投标文件分页文本
// Output: 保留有证据的具体缺陷；证据不足及资格性自动通过转人工
// Pos: compliance/domain/policy/ - 语义负结论证据边界（spec079）
// 维护声明: 与LLM上下文、EvidenceAssembler和图片敏感项契约同步。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 同一边界用于LLM入口及引擎最终输出，不能用标题或检索未命中证明全篇缺失。 */
final class SemanticEvidenceBoundary {
    private static final Set<String> GUARDED_ITEMS = Set.of(
            BidQualityCheckItem.AUTHORIZATION_LETTER.getCode(), BidQualityCheckItem.ID_AUTH_LETTER.getCode(),
            BidQualityCheckItem.FRONT_TABLE_RESPONSE.getCode(), BidQualityCheckItem.QUALIFICATION_REVIEW.getCode());
    private static final List<String> ABSENCE_CLAIMS = List.of(
            "缺失", "缺少", "未提供", "未包含", "未附", "未检测到", "未检索到", "未见", "正文中没有", "原文没有", "缺页");
    private static final List<String> FINANCIAL_CUES = List.of("财务", "审计", "资产负债表", "利润表", "现金流量表");
    private static final Pattern SELF_ADMISSION = Pattern.compile(
            "^(?:(?:财务材料说明|材料说明|说明)[：:])?\\s*(?:我司|本公司|本投标人|我方|本投标文件)\\s*(?:确实|确认|本次)?\\s*(?:未|不)(?:提供|提交|附具|报送|附)(.+)");
    private static final Pattern CONCRETE_BLANK = Pattern.compile(
            "(授权期限|授权范围|受托人|法定代表人|交货期|质保期|付款方式|投标有效期)[：:]\\s*_{3,}");

    private SemanticEvidenceBoundary() { }

    static QualityCheckItemResult reconcile(final QualityCheckItemResult input, final DocumentContent bidDoc) {
        if (input != null && input.status() == QualityCheckStatus.PASS
                && BidQualityCheckItem.ID_AUTH_LETTER.getCode().equals(input.itemCode())
                && bidDoc != null && bidDoc.pages().stream().anyMatch(
                        page -> page.text().contains(ScanPageTextPolicy.TRANSCRIPTION_MARKER))) {
            return QualityCheckItemResult.manualReview(BidQualityCheckItem.ID_AUTH_LETTER,
                    "已识别扫描页文字，但文字及身份证号码不能证明证件正反面齐全、身份一致或授权签署有效",
                    "请核对扫描页中的实际身份证正反面、人员一致性及签署信息；文字转写不等于图像附件核验通过",
                    input.evidences());
        }
        final QualityCheckItemResult result = requireQualificationConfirmation(input);
        if (result == null || result.status() != QualityCheckStatus.FAIL || !GUARDED_ITEMS.contains(result.itemCode())) return result;
        boolean financialClaim = result.itemCode().equals(BidQualityCheckItem.QUALIFICATION_REVIEW.getCode())
                && FINANCIAL_CUES.stream().anyMatch(result.conclusion()::contains);
        List<QualityEvidence> body = result.evidences().stream().filter(e -> isBody(e, bidDoc))
                .filter(e -> !financialClaim || FINANCIAL_CUES.stream().anyMatch(e.quote()::contains)).toList();
        boolean absenceClaim = ABSENCE_CLAIMS.stream().anyMatch(result.conclusion()::contains);
        List<String> topics = proofTopics(result.itemCode(), financialClaim);
        boolean provenSpecificDefect = body.stream().anyMatch(e -> explicitDefect(sourceText(e, bidDoc), topics));
        if (!body.isEmpty() && (!absenceClaim || provenSpecificDefect)) return result;
        return new QualityCheckItemResult(result.itemCode(), result.category(), result.title(), result.description(),
                QualityCheckStatus.MANUAL_REVIEW, result.severity(), result.order(),
                "[" + ManualReasons.EVIDENCE_INSUFFICIENT + "] 未定位到足以证明该缺陷的正文事实，目录、标题或检索缺口不能证明原文件缺失",
                "请对照原文件完整页面及图片附件人工复核后确认，不要仅凭检索未命中补删材料",
                result.evidences(), result.details());
    }

    /** 财务条款解析尚未完成适用条件证明，复合资格项不得由规则或AI自动放行。 */
    static QualityCheckItemResult requireQualificationConfirmation(final QualityCheckItemResult result) {
        if (result == null || result.status() != QualityCheckStatus.PASS
                || !BidQualityCheckItem.QUALIFICATION_REVIEW.getCode().equals(result.itemCode())) return result;
        return new QualityCheckItemResult(result.itemCode(), result.category(), result.title(), result.description(),
                QualityCheckStatus.MANUAL_REVIEW, result.severity(), result.order(),
                "财务要求适用性及资格材料尚需人工确认，系统暂不自动判定资格性评审通过",
                "请对照招标原文逐项确认财务条款、豁免条件及原始报表；已有定位证据仅供复核",
                result.evidences(), result.details());
    }

    private static boolean isBody(final QualityEvidence evidence, final DocumentContent bidDoc) {
        return "BID".equals(evidence.sourceType()) && evidence.pageNumber() != null && evidence.pageNumber() > 0
                && !evidence.quote().isBlank() && !BidLetterCheckPolicy.isCatalogOrOutlinePage(sourceText(evidence, bidDoc));
    }

    private static String sourceText(final QualityEvidence evidence, final DocumentContent bidDoc) {
        return bidDoc == null ? evidence.quote() : bidDoc.pages().stream()
                .filter(p -> p.pageNumber() == evidence.pageNumber()).findFirst()
                .map(DocumentContent.PageContent::text).orElse(evidence.quote());
    }

    private static List<String> proofTopics(final String itemCode, final boolean financialClaim) {
        if (financialClaim) return FINANCIAL_CUES;
        if (BidQualityCheckItem.AUTHORIZATION_LETTER.getCode().equals(itemCode)) {
            return List.of("授权委托书", "法定代表人身份证明", "授权期限", "授权范围", "受托人", "法定代表人");
        }
        if (BidQualityCheckItem.ID_AUTH_LETTER.getCode().equals(itemCode)) return List.of("身份证", "公民身份号码");
        if (BidQualityCheckItem.FRONT_TABLE_RESPONSE.getCode().equals(itemCode)) {
            return List.of("前附表", "条款响应", "商务响应表", "交货期", "质保期", "付款方式", "投标有效期", "质量标准", "交货地点", "工期");
        }
        return List.of("资格", "纳税", "社保", "信用", "信誉", "业绩");
    }

    private static boolean explicitDefect(final String text, final List<String> topics) {
        Matcher blank = CONCRETE_BLANK.matcher(text);
        while (blank.find()) {
            String field = blank.group(1);
            if (topics.stream().anyMatch(field::contains)) return true;
        }
        if (List.of("示例", "模板", "填写", "范例", "参考").stream().anyMatch(text::contains)) return false;
        for (String clause : text.split("[。；;\\r\\n]+")) {
            Matcher admission = SELF_ADMISSION.matcher(clause.trim());
            if (admission.matches()) {
                String object = admission.group(1);
                if (topics.stream().anyMatch(object::contains)) return true;
            }
        }
        return false;
    }
}
