// Input: 规则/LLM裁决结果与投标文件分页文本及图像实证
// Output: 协调明确证据、授权及资格结果；证书文本判定委托独立纯策略
// Pos: compliance/domain/policy/ - 语义证据边界，协调明确证据及人工复核裁决（spec083）
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
    private static final Set<String> GUARDED_ITEMS =
            Set.of(BidQualityCheckItem.AUTHORIZATION_LETTER.getCode(), BidQualityCheckItem.ID_AUTH_LETTER.getCode(),
                    BidQualityCheckItem.FRONT_TABLE_RESPONSE.getCode(),
                    BidQualityCheckItem.QUALIFICATION_REVIEW.getCode(), BidQualityCheckItem.SCORING_RESPONSE.getCode());
    private static final List<String> ABSENCE_CLAIMS =
            List.of("缺失", "缺少", "未提供", "未包含", "未附", "未检测到", "未检索到", "未见", "正文中没有", "原文没有", "缺页");
    private static final List<String> FINANCIAL_CUES = List.of("财务", "审计", "资产负债表", "利润表", "现金流量表");
    private static final Pattern SELF_ADMISSION =
            Pattern.compile(
                    "^(?:(?:财务材料说明|材料说明|说明)[：:])?\\s*(?:我司|本公司|本投标人|我方|"
                + "本投标文件)\\s*(?:确实|确认|本次)?\\s*(?:未|不)(?:提供|提交|附具|报送|附)(.+)");
    private static final String SCORING_FIELDS =
            "技术方案|实施方案|施工组织|项目经理(?:业绩(?:证明)?)?|项目管理(?:机构(?:配置)?)?|团队人员|服务方案|进度计划|技术架构|人员配置";
    private static final Pattern CONCRETE_BLANK =
            Pattern.compile("(?:(授权期限|授权范围|受托人|法定代表人|交货期|质保期|付款方式|投标有效期|" + SCORING_FIELDS
                    + ")[：:]\\s*(?:_{3,}|[【\\[（(](?:\\s*|(?:请\\s*)?(?:在此处|在此|此处)?(?:填写|"
                + "输入|插入|粘贴|待填)[^】\\]）)]{0,30})[】\\]）)]))|"
                + "(?:[【\\[（(](?:请\\s*)?(?:在此处|在此|此处)?(?:填写|输入|插入|粘贴|"
                + "待填)[^】\\]）)]{0,10}("
                            + SCORING_FIELDS + ")[^】\\]）)]{0,20}[】\\]）)])");
    private static final Pattern CERT_NUMBER_FACT =
            Pattern.compile(
                    "(?:许可证编号|证书编号|资质编号|认证编号|备案编号|证号|注册号)[\\s\\u3000|"
                + ":：为是]*[A-Za-z0-9\\u4e00-\\u9fa5-]{4,}|合字[A-Z0-9-]{4,}");
    private static final Pattern CERT_VALIDITY_FACT =
            Pattern.compile("(?:有效期限|有效期)[^。；\\n]{0,40}(?:20[2-9]\\d\\s*(?:年|[./-])|长期)");
    private SemanticEvidenceBoundary() {}
    static QualityCheckItemResult reconcileQualificationApplicability(final QualityCheckItemResult result,
            final DocumentContent bidDoc, final TenderRequirements requirements) {
        if (!BidQualityCheckItem.QUALIFICATION_MATCH.getCode().equals(result.itemCode())
                || !requirements.hasUnresolvedCertificateRequirements()) return result;
        var firstStage = QualificationMatchPolicy.checkFirstStage(bidDoc, requirements);
        if (firstStage.filter(v -> v.status() == QualityCheckStatus.FAIL).isPresent())
            return firstStage.orElseThrow();
        return QualityCheckItemResult.manualReview(BidQualityCheckItem.QUALIFICATION_MATCH,
                "证书条件的强制性或作用对象尚未确认，资料检索结果不能直接证明招标资格满足或缺失", "请按结构化条件的原文依据确认适用范围，再核对企业资质与本次投标材料", result.evidences());
    }

    static QualityCheckItemResult reconcile(final QualityCheckItemResult input, final DocumentContent bidDoc) {
        QualityCheckItemResult passCheck = reconcilePass(input, bidDoc);
        if (passCheck != null) return passCheck;
        return downgradeGuardedFail(requireQualificationConfirmation(input, bidDoc), bidDoc);
    }

    private static QualityCheckItemResult reconcilePass(
            final QualityCheckItemResult input, final DocumentContent bidDoc) {
        if (input == null || input.status() != QualityCheckStatus.PASS) return null;
        if (BidQualityCheckItem.ID_AUTH_LETTER.getCode().equals(input.itemCode())
                && !hasCompleteBothSidesIdFacts(bidDoc))
            return ManualReasons.manual(BidQualityCheckItem.ID_AUTH_LETTER, ManualReasons.EVIDENCE_INSUFFICIENT,
                    "文本及图片标记不能证明证件正反面齐全、身份一致或授权签署有效", "请核对扫描页中的实际身份证正反面、人员一致性及签署信息；文字转写不等于图像附件核验通过", input.evidences());
        if (BidQualityCheckItem.AUTHORIZATION_LETTER.getCode().equals(input.itemCode()) && bidDoc != null
                && bidDoc.pages().stream().anyMatch(p ->
                                        p.text().contains(ScanPageTextPolicy.TRANSCRIPTION_MARKER)
                                                && BasicInfoCheckPolicy.containsAnyKeyword(p.text(),
                                                        AuthorizationLetterCheckPolicy.AUTH_LETTER_KEYWORDS))
                && !AuthorizationFieldPolicy.hasSignedAuthorizationEvidence(bidDoc))
            return ManualReasons.manual(BidQualityCheckItem.AUTHORIZATION_LETTER, ManualReasons.EVIDENCE_INSUFFICIENT,
                    "授权委托书未检测到签字盖章痕迹或签署图像，文字字段不能单独证明授权已生效", "请核对原始授权书的签字盖章及签署有效性后确认", input.evidences());
        return null;
    }

    private static QualityCheckItemResult downgradeGuardedFail(
            final QualityCheckItemResult result, final DocumentContent bidDoc) {
        if (result == null || result.status() != QualityCheckStatus.FAIL
                || !GUARDED_ITEMS.contains(result.itemCode())) return result;
        boolean financialClaim =
                result.itemCode().equals(BidQualityCheckItem.QUALIFICATION_REVIEW.getCode())
                        && FINANCIAL_CUES.stream().anyMatch(result.conclusion()::contains);
        boolean scoring = BidQualityCheckItem.SCORING_RESPONSE.getCode().equals(result.itemCode());
        List<QualityEvidence> body =
                result.evidences().stream().filter(e -> isBody(e, bidDoc)).filter(e ->
                                        !scoring || bidDoc != null && bidDoc.pages().stream().anyMatch(p ->
                                                                                e.pageNumber().equals(p.pageNumber())
                                                                                        && p.text()
                                                                                                != null && p.text()
                                                                                                .contains(e.quote())))
                        .filter(e ->
                                        !financialClaim || FINANCIAL_CUES.stream().anyMatch(e.quote()::contains))
                        .toList();
        boolean absenceClaim = ABSENCE_CLAIMS.stream().anyMatch(result.conclusion()::contains);
        List<String> topics = proofTopics(result.itemCode(), financialClaim);
        boolean provenSpecificDefect = body.stream().anyMatch(e -> explicitDefect(
                sourceText(e, bidDoc), topics, scoring ? precedingText(bidDoc, e.pageNumber()) : ""));
        if (!body.isEmpty() && ((!scoring && !absenceClaim) || provenSpecificDefect)) return result;
        return new QualityCheckItemResult(result.itemCode(), result.category(), result.title(), result.description(),
                QualityCheckStatus.MANUAL_REVIEW, result.severity(), result.order(), "["
                        + ManualReasons.EVIDENCE_INSUFFICIENT + "] 未定位到足以证明该缺陷的正文事实，目录、标题或检索缺口不能证明原文件缺失",
                "请对照原文件完整页面及图片附件人工复核后确认，不要仅凭检索未命中补删材料", result.evidences(), result.details());
    }

    private static String precedingText(final DocumentContent document, final int pageNumber) {
        return document.pages().stream()
                .filter(page -> page.pageNumber() < pageNumber && page.text() != null)
                .sorted(java.util.Comparator.comparingInt(DocumentContent.PageContent::pageNumber))
                .map(DocumentContent.PageContent::text)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    static QualityCheckItemResult requireQualificationConfirmation(final QualityCheckItemResult result) {
        return requireQualificationConfirmation(result, null);
    }

    static QualityCheckItemResult requireQualificationConfirmation(
            final QualityCheckItemResult result, final DocumentContent bidDoc) {
        if (result == null || result.status() != QualityCheckStatus.PASS
                || !(BidQualityCheckItem.QUALIFICATION_REVIEW.getCode().equals(result.itemCode())
                        || BidQualityCheckItem.QUALIFICATION_VETO.getCode().equals(result.itemCode()))) return result;
        boolean veto = BidQualityCheckItem.QUALIFICATION_VETO.getCode().equals(result.itemCode());
        // A certificate proves one fact, never the entire qualification checklist.

        return new QualityCheckItemResult(result.itemCode(), result.category(), result.title(), result.description(),
                QualityCheckStatus.MANUAL_REVIEW, result.severity(), result.order(), veto
                        ? "完整资格条件及证明材料尚需逐项核验，证书名称或资料库匹配不能证明资格全部满足"
                        : "财务要求适用性及资格材料尚需人工确认，系统暂不自动判定资格性评审通过", veto
                        ? "请对照招标资格审查清单逐项复核主体、财务、信用、业绩、纳税社保及本次投标证明材料"
                        : "请对照招标原文逐项确认财务条款、豁免条件及原始报表；已有定位证据仅供复核", result.evidences(), result.details());
    }

    static boolean hasSubstantiveCertificateFacts(final DocumentContent bidDoc, final Integer pageNumber) {
        return bidDoc != null && pageNumber != null && bidDoc.pages().stream().filter(p -> p.pageNumber() == pageNumber)
                        .map(DocumentContent.PageContent::text)
                        .anyMatch(SemanticEvidenceBoundary::hasSubstantiveCertificateFacts);
    }

    static boolean hasSubstantiveCertificateFacts(final String text) {
        return text != null && !text.isBlank() && CERT_NUMBER_FACT.matcher(text).find()
                && CERT_VALIDITY_FACT.matcher(text).find();
    }

    static boolean hasPositiveCertificateStatement(final String text, final String certificate) {
        return CertificateEvidenceContextPolicy.hasPositiveCertificateStatement(text, certificate);
    }

    private static boolean hasCompleteBothSidesIdFacts(final DocumentContent bidDoc) {
        if (bidDoc == null || bidDoc.pages() == null || bidDoc.images().isEmpty()) return false;
        return bidDoc.pages().stream().filter(p ->
                                p.text() != null && !BidLetterCheckPolicy.isCatalogOrOutlinePage(p.text()))
                .filter(p -> AuthorizationFieldPolicy.hasImageOnPage(bidDoc, p.pageNumber())).anyMatch(p ->
                                p.text().contains(ScanPageTextPolicy.TRANSCRIPTION_MARKER)
                                        && IdAuthLetterCheckPolicy.hasCompleteBothSidesIdFacts(p.text().substring(
                                                                p.text().indexOf(ScanPageTextPolicy
                                                                                        .TRANSCRIPTION_MARKER))));
    }

    private static boolean isBody(final QualityEvidence evidence, final DocumentContent bidDoc) {
        return "BID".equals(evidence.sourceType()) && evidence.pageNumber() != null && evidence.pageNumber() > 0
                && !evidence.quote().isBlank()
                && !BidLetterCheckPolicy.isCatalogOrOutlinePage(sourceText(evidence, bidDoc));
    }

    private static String sourceText(final QualityEvidence evidence, final DocumentContent bidDoc) {
        if (bidDoc == null || evidence == null || evidence.pageNumber() == null)
            return evidence == null ? "" : evidence.quote();
        return bidDoc.pages().stream().filter(p -> evidence.pageNumber().equals(p.pageNumber())).findFirst()
                .map(DocumentContent.PageContent::text).orElse(evidence.quote());
    }

    private static List<String> proofTopics(final String itemCode, final boolean financialClaim) {
        if (financialClaim) return FINANCIAL_CUES;
        if (BidQualityCheckItem.SCORING_RESPONSE.getCode().equals(itemCode))
            return List.of("技术方案", "实施方案", "施工组织", "项目经理", "项目管理", "团队人员", "服务方案", "进度计划", "技术架构", "人员配置");
        if (BidQualityCheckItem.AUTHORIZATION_LETTER.getCode().equals(itemCode))
            return List.of("授权委托书", "法定代表人身份证明", "授权期限", "授权范围", "受托人", "法定代表人");
        if (BidQualityCheckItem.ID_AUTH_LETTER.getCode().equals(itemCode))
            return List.of("身份证", "公民身份号码");
        if (BidQualityCheckItem.FRONT_TABLE_RESPONSE.getCode().equals(itemCode))
            return List.of("前附表", "条款响应", "商务响应表", "交货期", "质保期", "付款方式", "投标有效期", "质量标准", "交货地点", "工期");
        return List.of("资格", "纳税", "社保", "信用", "信誉", "业绩");
    }

    private static boolean scoringExampleContext(final String rawClause, final boolean previous) {
        return !CertificateEvidenceContextPolicy.isFormalBodyHeading(rawClause)
                && (CertificateEvidenceContextPolicy.isExampleHeading(rawClause) || previous);
    }

    private static boolean explicitDefect(final String text, final List<String> topics, final String precedingText) {
        boolean scoring = topics.contains("技术方案"), exampleContext = false;
        if (scoring)
            for (String clause : precedingText.split("[。；;\\r\\n]+"))
                exampleContext = scoringExampleContext(clause.trim(), exampleContext);
        if (!scoring) {
            Matcher blank = CONCRETE_BLANK.matcher(text);
            while (blank.find())
                if (topics.stream().anyMatch((blank.group(1) != null ? blank.group(1) : blank.group(2))
                                        ::contains)) return true;
            if (List.of("示例", "模板", "填写", "范例", "参考").stream().anyMatch(text::contains))
                return false;
        }

        for (String rawClause : text.split("[。；;\\r\\n]+")) {
            String clause = rawClause.trim();
            if (scoring) {
                // 样例标题约束后续字段；普通称谓和其他分句的参考/填写说明不抹掉真实缺陷。
                exampleContext = scoringExampleContext(clause, exampleContext);
                if (exampleContext || clause.matches(".*(?:示例|模板|范例|样例|样本|参考格式).*")) continue;
                Matcher blank = CONCRETE_BLANK.matcher(clause);
                while (blank.find())
                    if (topics.stream().anyMatch((blank.group(1) != null ? blank.group(1) : blank.group(2))
                                            ::contains)) return true;
            }

            Matcher admission = SELF_ADMISSION.matcher(clause);
            if (admission.matches() && topics.stream().anyMatch(admission.group(1)::contains))
                return true;
        }

        return false;
    }
}
