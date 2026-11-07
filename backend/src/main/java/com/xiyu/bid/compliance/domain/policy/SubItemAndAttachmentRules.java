// Input: 投标文件 DocumentContent
// Output: QualityCheckItemResult（各项分项合规结果；否定清单中自身谓语起点否定才豁免，未知自身语境待确认）
// Pos: compliance/domain/policy/ - 分项合规与附件清单规则策略（纯核心，含承诺书签章反假）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;
import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * 分项合规与附件清单规则策略 (FP-Java).
 */
public final class SubItemAndAttachmentRules {
    private static final Pattern SENSITIVE_VETO_PATTERN = Pattern.compile(
            "(转包|违法分包|串通投标|围标|弄虚作假|挂靠资质|以他人名义投标|自愿放弃中标资格|拒绝承担质保责任)"
    );
    private SubItemAndAttachmentRules() {
    }

    public static QualityCheckItemResult checkDocumentCompleteness(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.DOCUMENT_COMPLETENESS;
        if (bidDoc.fullText().isBlank()) {
            return QualityCheckItemResult.fail(item, "投标文件文本为空，无法核对组成", "请上传完整可读取的投标文件", Collections.emptyList());
        }
        BasicInfoCheckPolicy.PageMatch match = BasicInfoCheckPolicy.findFirstPageContaining(bidDoc, "目录", "目 录");
        List<QualityEvidence> evidences = match == null ? List.of() : List.of(QualityEvidence.bid(
                "目录定位线索", match.pageNumber(), BasicInfoCheckPolicy.snippet(match.text(), 200)));
        return ManualReasons.manual(item, ManualReasons.EVIDENCE_INSUFFICIENT,
                "已读取投标文件文本，但文本片段数量不能证明招标要求的全部卷册与章节齐全",
                "请对照招标文件的组成清单逐项核对卷册、章节及图片附件", evidences);
    }

    public static QualityCheckItemResult checkTemplateFormat(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.TEMPLATE_FORMAT;
        if (bidDoc.fullText().isBlank()) {
            return QualityCheckItemResult.fail(item, "文本为空，无法核对格式", "请上传可读取的投标文件", Collections.emptyList());
        }
        return ManualReasons.manual(item, ManualReasons.EVIDENCE_INSUFFICIENT,
                "已读取正文文字，文本抽取结果不包含可验证的字体、字号、页边距和版式信息",
                "请对照招标模板在原始文件中核对字体、字号、页边距及页码", Collections.emptyList());
    }

    public static QualityCheckItemResult checkVetoKeyword(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.VETO_KEYWORD;
        List<QualityEvidence> hits = new ArrayList<>();
        List<QualityEvidence> uncertain = new ArrayList<>();
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            String text = page.text();
            Matcher m = SENSITIVE_VETO_PATTERN.matcher(text);
            while (m.find()) {
                var context = RuleCitationContext.sensitiveContext(text, m.start(), m.end(), SENSITIVE_VETO_PATTERN);
                if (context == RuleCitationContext.SensitiveContext.DENIED
                        || context == RuleCitationContext.SensitiveContext.REFERENCE) continue;
                var evidence = QualityEvidence.bid("敏感词原位事实", page.pageNumber(),
                        BasicInfoCheckPolicy.snippetAt(text, m.start(), m.end(), 150));
                if (context == RuleCitationContext.SensitiveContext.AFFIRMED) hits.add(evidence);
                else uncertain.add(evidence);
            }
        }
        if (hits.isEmpty() && !uncertain.isEmpty()) return ManualReasons.manual(item, ManualReasons.AMBIGUOUS,
                "敏感词与自身事实尚未可靠绑定", "请核对原始条目是条件引用还是实际情况陈述", uncertain);
        if (hits.isEmpty()) {
            return QualityCheckItemResult.pass(item, "未扫描到任何废标、转包、挂靠等高危违规关键词（正向履约承诺已合规核验）", Collections.emptyList());
        }
        return QualityCheckItemResult.fail(item, "在投标文件中扫描到 " + hits.size() + " 处高危/废标敏感词！", "请立刻在标书中排查并删除相关敏感词句", hits);
    }

    public static QualityCheckItemResult checkCommitmentSeal(final DocumentContent bidDoc) {
        return CommitmentSealCheckPolicy.evaluateCommitmentSignature(bidDoc);
    }

    public static QualityCheckItemResult checkIdAuthLetter(final DocumentContent bidDoc) {
        return checkIdAuthLetter(bidDoc, IdAuthLetterRequirement.standard());
    }

    public static QualityCheckItemResult checkIdAuthLetter(
            final DocumentContent bidDoc, final IdAuthLetterRequirement requirement) {
        return IdAuthLetterCheckPolicy.check(bidDoc, requirement);
    }

    public static QualityCheckItemResult checkBidOpeningTable(final DocumentContent bidDoc) {
        return BidOpeningTableCheckPolicy.check(bidDoc);
    }

    public static QualityCheckItemResult checkDepositReceipt(final DocumentContent bidDoc) {
        return checkDepositReceipt(bidDoc, (String) null);
    }

    public static QualityCheckItemResult checkDepositReceipt(final DocumentContent bidDoc, final String tenderText) {
        BidQualityCheckItem item = BidQualityCheckItem.DEPOSIT_RECEIPT;
        if (isDepositNotRequired(tenderText)) {
            return QualityCheckItemResult.pass(item, "【不涉及】招标文件未要求缴纳投标保证金/响应担保（免收/不涉及）", Collections.emptyList());
        }
        BasicInfoCheckPolicy.PageMatch strongMatch = BasicInfoCheckPolicy.findFirstPageContaining(bidDoc,
                "投标保证金汇款", "保证金转账", "银行汇款回单", "电子保函凭证", "银行电子回单", "保证金缴纳凭证");
        if (strongMatch != null && isActualDepositVoucherPage(strongMatch.text())) {
            return QualityCheckItemResult.pass(item, "已附投标保证金汇款回单或电子保函凭证",
                    List.of(QualityEvidence.bid("保证金凭证页", strongMatch.pageNumber(),
                            BasicInfoCheckPolicy.snippetAround(strongMatch.text(), 200,
                                    "投标保证金", "银行回单", "转账"))));
        }
        BasicInfoCheckPolicy.PageMatch guaranteeMatch = BasicInfoCheckPolicy.findFirstPageContaining(bidDoc,
                "投标保函", "银行保函", "电子保函", "保函凭证", "保函原件");
        if (guaranteeMatch != null && isActualGuaranteePage(guaranteeMatch.text())) {
            return QualityCheckItemResult.pass(item, "已附投标保证金汇款回单或电子保函凭证",
                    List.of(QualityEvidence.bid("保证金凭证页", guaranteeMatch.pageNumber(),
                            BasicInfoCheckPolicy.snippetAround(guaranteeMatch.text(), 200,
                                    "保函", "投标保证金"))));
        }
        // 若标书中声明免收/不涉及，仅当招标文件未明确要求缴纳保证金时，才认可该声明
        if (bidDoc.fullText().contains("不收取投标保证金") || bidDoc.fullText().contains("免收保证金")
                || bidDoc.fullText().contains("不涉及保证金") || bidDoc.fullText().contains("免交投标保证金")
                || bidDoc.fullText().contains("免收投标担保") || bidDoc.fullText().contains("不涉及投标担保")
                || bidDoc.fullText().contains("免收响应担保") || bidDoc.fullText().contains("不涉及响应担保")
                || bidDoc.fullText().contains("不要求递交响应担保") || bidDoc.fullText().contains("不要求提交响应担保")) {
            DepositRequirement tenderReq = DeterministicDepositExtractor.extract(tenderText);
            if (tenderReq == null || Boolean.FALSE.equals(tenderReq.required())) {
                return QualityCheckItemResult.pass(item, "【不涉及】投标文件中声明不涉及/免收投标保证金/响应担保", Collections.emptyList());
            }
        }
        if (strongMatch != null || guaranteeMatch != null) {
            return QualityCheckItemResult.manualReview(item, "检索到保证金或保函相关表述，但无法确认为实际凭证（可能为目录、格式模板或清单占位符）",
                    "请人工核实投标文件是否附有正式银行汇款回单或电子保函凭证", Collections.emptyList());
        }
        if (containsDepositPaymentDeclaration(bidDoc.fullText())) {
            return QualityCheckItemResult.manualReview(item, "投标文件声明已缴纳保证金或回单附后，但文本层未能读取实际凭证（可能为扫描图片）", "请人工核实投标文件中的银行汇款回单或电子保函图片", Collections.emptyList());
        }
        return QualityCheckItemResult.fail(item, "未检索到投标保证金缴纳凭证或投标保函", "请补充投标保证金转账回执单扫描件或有效保函", Collections.emptyList());
    }

    private static boolean containsDepositPaymentDeclaration(final String text) {
        if (text == null || text.isBlank()) return false;
        boolean mentionsDeposit = text.contains("投标保证金") || text.contains("响应担保");
        boolean claimsPayment = text.contains("已缴纳") || text.contains("已交纳") || text.contains("回单附后") || text.contains("凭证附后");
        return mentionsDeposit && claimsPayment;
    }

    public static QualityCheckItemResult checkCaCertificate(final DocumentContent bidDoc) {
        return checkCaCertificate(bidDoc, (CaCertificateRequirement) null);
    }

    public static QualityCheckItemResult checkCaCertificate(final DocumentContent bidDoc, final String tenderText) {
        CaCertificateRequirement req = tenderText != null
                ? DeterministicCaCertificateExtractor.extract(DocumentContent.fromText("tender.txt", "hash", tenderText))
                : null;
        return checkCaCertificate(bidDoc, req);
    }

    public static QualityCheckItemResult checkCaCertificate(final DocumentContent bidDoc, final CaCertificateRequirement requirement) {
        return checkCaCertificate(bidDoc, requirement, Collections.emptyList());
    }

    public static QualityCheckItemResult checkCaCertificate(
            final DocumentContent bidDoc, final CaCertificateRequirement requirement, final List<QualityEvidence> kbEvidences) {
        return CaCertificateCheckPolicy.check(bidDoc, requirement, kbEvidences);
    }

    public static QualityCheckItemResult checkDepositRefund(final DocumentContent bidDoc) {
        return checkDepositRefund(bidDoc, null);
    }

    public static QualityCheckItemResult checkDepositRefund(final DocumentContent bidDoc, final String tenderText) {
        BidQualityCheckItem item = BidQualityCheckItem.DEPOSIT_REFUND;
        if (isDepositNotRequired(tenderText)) {
            return QualityCheckItemResult.pass(item, "【不涉及】本项目不涉及投标保证金/响应担保，无需提供退款账户信息", Collections.emptyList());
        }
        String[] refundCues = {"退还保证金", "保证金退还", "退还开户银行", "退还账户", "退保证金", "保证金退款信息"};
        for (DocumentContent.PageContent page : bidDoc == null ? DocumentContent.empty().pages() : bidDoc.pages()) {
            String text = page.text();
            if (BidLetterCheckPolicy.isCatalogOrOutlinePage(text)
                    || !BasicInfoCheckPolicy.containsAnyKeyword(text, refundCues)
                    || text.contains("请填写") || text.contains("待填写") || text.contains("参考模板")) continue;
            if (RefundAccountEvidencePolicy.hasCompleteFacts(text)) {
                return QualityCheckItemResult.pass(item, "已定位保证金退款账户户名、开户银行及账号", List.of(
                        QualityEvidence.bid("退款账户信息页", page.pageNumber(), BasicInfoCheckPolicy.snippetAround(
                                text, 240, "户名", "开户银行", "银行账号"))));
            }
        }
        return QualityCheckItemResult.manualReview(item,
                "保证金退款材料适用性及账户信息尚待确认，未定位到完整退款账户事实",
                "请先核对招标文件是否要求单独提交退款信息表；仅有退还流程或关键词不能证明材料齐全，也不能认定缺失",
                Collections.emptyList());
    }

    private static boolean isDepositNotRequired(final String tenderText) {
        return DeterministicDepositExtractor.isDepositNotRequired(tenderText);
    }

    /**
     * 二次验证：命中保证金回单关键词的页面是否为实际回单凭证，而非目录/格式要求/清单占位符/纯文字合规声明.
     *
     * <p>凭证事实要素原则：页面必须包含真实凭证的具体事实要素（凭证类型标志 + 交易事实特征），
     * 缺一不可；坚决排除目录点线（......）、占位声明与无凭证正文.
     */
    static boolean isActualDepositVoucherPage(final String pageText) {
        if (pageText == null || pageText.isBlank()) {
            return false;
        }
        // 1. 结构化特征识别：目录页排除（目录点线、TOC 标题、仅列出页码等）
        if (pageText.contains("......") || pageText.contains("……")
                || pageText.contains("目录") || pageText.contains("目 录")) {
            if (!pageText.contains("电子回单") && !pageText.contains("业务凭证") && !pageText.contains("回执单")) {
                return false;
            }
        }

        // 2. 占位与模板排除
        boolean isPlaceholderOrRequirement = pageText.contains("格式范本") || pageText.contains("参考模板")
                || pageText.contains("（如有）") || pageText.contains("（不涉及）")
                || pageText.contains("此处粘贴") || pageText.contains("附后")
                || pageText.contains("须附") || pageText.contains("应当提供");
        if (isPlaceholderOrRequirement && !pageText.contains("流水号") && !pageText.contains("交易号")) {
            return false;
        }

        // 3. 强制事实要素（必须同时满足：凭证类型标志 + 交易事实数据特征）
        boolean hasVoucherType = pageText.contains("电子回单") || pageText.contains("银行回单")
                || pageText.contains("业务凭证") || pageText.contains("回执单")
                || pageText.contains("转账凭据") || pageText.contains("业务回单")
                || pageText.contains("网银电子回单") || pageText.contains("转账凭证")
                || pageText.contains("电子保函凭证") || pageText.contains("缴费凭条");

        boolean hasTransactionDetail = pageText.contains("流水号") || pageText.contains("交易号")
                || pageText.contains("凭证号") || pageText.contains("回单号")
                || pageText.contains("付款人") || pageText.contains("收款人")
                || pageText.contains("转账成功") || pageText.contains("交易金额")
                || pageText.contains("汇款金额") || pageText.contains("电子印章")
                || pageText.contains("回单专用章") || pageText.contains("业务验证码");

        // 强制事实约束：必须同时具备凭证类型与具体交易要素
        return hasVoucherType && hasTransactionDetail;
    }

    /**
     * 二次验证：命中保函关键词的页面是否为实际保函凭证，而非格式要求/附件清单/合规声明中的引用.
     * 必须具备保函事实要素（保函编号/保函金额/出具机构等）且排除格式模板.
     */
    static boolean isActualGuaranteePage(final String pageText) {
        if (pageText == null || pageText.isBlank()) {
            return false;
        }
        if (pageText.contains("......") || pageText.contains("……")
                || pageText.contains("目录") || pageText.contains("目 录")) {
            if (!pageText.contains("保函编号") && !pageText.contains("保函号")) {
                return false;
            }
        }
        boolean hasGuaranteeDetail = pageText.contains("保函编号") || pageText.contains("保函金额")
                || pageText.contains("出具机构") || pageText.contains("担保机构")
                || pageText.contains("保函号") || pageText.contains("担保金额")
                || pageText.contains("保函有效期") || pageText.contains("不可撤销")
                || (pageText.contains("受益人") && pageText.contains("保证人"));

        boolean isTemplateOrFormat = pageText.contains("格式范本") || pageText.contains("参考模板")
                || pageText.contains("保函格式") || pageText.contains("须提交保函")
                || pageText.contains("此处粘贴");

        return hasGuaranteeDetail && !isTemplateOrFormat;
    }
}
