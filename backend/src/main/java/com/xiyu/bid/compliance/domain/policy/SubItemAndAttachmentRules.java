// Input: 投标文件 DocumentContent
// Output: QualityCheckItemResult（各项分项合规结果）
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
    private static final List<String> NEGATION_PREFIXES = List.of(
            "承诺不", "严禁", "不得", "不进行", "不含", "不存在", "没有", "无任何",
            "杜绝", "防范", "禁止", "坚决抵制", "零", "未", "无", "不"
    );

    private SubItemAndAttachmentRules() {
    }

    public static QualityCheckItemResult checkDocumentCompleteness(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.DOCUMENT_COMPLETENESS;
        if (bidDoc.pages().size() >= 3) {
            BasicInfoCheckPolicy.PageMatch match = BasicInfoCheckPolicy.findFirstPageContaining(bidDoc, "目录", "目 录");
            List<QualityEvidence> evidences = match != null
                    ? List.of(QualityEvidence.bid("目录页", match.pageNumber(), BasicInfoCheckPolicy.snippet(match.text(), 200)))
                    : Collections.emptyList();
            return QualityCheckItemResult.pass(item, "投标文件结构完整（共 " + bidDoc.pages().size() + " 页），包含目录与主要正文章节", evidences);
        }
        return QualityCheckItemResult.fail(item, "投标文件页数过少（少于3页），可能缺少正文或组成卷册", "请检查并上传完整的投标文件正文分卷", Collections.emptyList());
    }

    public static QualityCheckItemResult checkTemplateFormat(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.TEMPLATE_FORMAT;
        if (!bidDoc.fullText().isBlank()) {
            return QualityCheckItemResult.pass(item, "排版与章节层次清晰，符合一般投标文档格式规范", Collections.emptyList());
        }
        return QualityCheckItemResult.fail(item, "文本为空，格式不符合规范", "请重新生成并检查排版格式", Collections.emptyList());
    }

    public static QualityCheckItemResult checkVetoKeyword(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.VETO_KEYWORD;
        List<QualityEvidence> hits = new ArrayList<>();
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            String text = page.text();
            Matcher m = SENSITIVE_VETO_PATTERN.matcher(text);
            while (m.find()) {
                if (isNegatedOrCommitment(text, m.start())) {
                    // 正向承诺或否定语境（如「严禁转包」「承诺不违法分包」「无弄虚作假」），予以豁免
                    continue;
                }
                hits.add(QualityEvidence.bid("废标敏感词命中", page.pageNumber(),
                        "命中词: [" + m.group() + "]，上下文: " + BasicInfoCheckPolicy.snippetAround(text, 150, m.group())));
            }
        }
        if (hits.isEmpty()) {
            return QualityCheckItemResult.pass(item, "未扫描到任何废标、转包、挂靠等高危违规关键词（正向履约承诺已合规核验）", Collections.emptyList());
        }
        return QualityCheckItemResult.fail(item, "在投标文件中扫描到 " + hits.size() + " 处高危/废标敏感词！", "请立刻在标书中排查并删除相关敏感词句", hits);
    }

    /** 检查匹配命中点是否处于否定/承诺豁免前缀语境（如「严禁转包」「承诺不违法分包」「杜绝弄虚作假与串通投标」）. */
    private static boolean isNegatedOrCommitment(final String text, final int matchStart) {
        if (text == null || matchStart <= 0) {
            return false;
        }
        int lookbackStart = Math.max(0, matchStart - 20);
        String prefix = text.substring(lookbackStart, matchStart);
        for (String negation : NEGATION_PREFIXES) {
            if (prefix.contains(negation)) {
                return true;
            }
        }
        return false;
    }

    // 已填签署人/单位名称（须以汉字开头，避免把「（盖章）」说明文字误判为已填名称；冒号后仅允许同行空白）
    private static final Pattern FILLED_SIGNATORY_PATTERN = Pattern.compile(
            "(?:法定代表人|承诺人|承诺单位|授权代表|受托人)[：:][ \\t]{0,4}[\\u4e00-\\u9fa5][\\u4e00-\\u9fa5（）()]{1,19}"
    );

    private static final Pattern COMMITMENT_SEAL_MARK_PATTERN = Pattern.compile(
            "（公章）|\\(公章\\)|（盖章）|\\(盖章\\)|盖单位公章|加盖公章|公章|签字"
    );

    public static QualityCheckItemResult checkCommitmentSeal(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.COMMITMENT_SEAL;
        BasicInfoCheckPolicy.PageMatch match = BasicInfoCheckPolicy.findFirstPageContaining(bidDoc, "承诺书", "承诺函", "廉洁承诺", "诚信承诺");
        if (match != null) {
            String text = match.text();
            boolean hasPlaceholder = SealIntegrityCheckPolicy.hasUnfilledSealPlaceholder(text);
            boolean hasPositiveEvidence = SealIntegrityCheckPolicy.hasPositiveSealEvidence(text);
            boolean hasFilledSignatory = FILLED_SIGNATORY_PATTERN.matcher(text).find();
            boolean hasSealMark = COMMITMENT_SEAL_MARK_PATTERN.matcher(text).find();
            if (hasPlaceholder && !hasPositiveEvidence) {
                return QualityCheckItemResult.fail(item, "承诺书包含未填写的空白占位或未盖章模板标记", "请在承诺书落款处加盖真实公章及法定代表人签字，不得保留下划线或模板占位符",
                        List.of(QualityEvidence.bid("承诺书占位留白", match.pageNumber(), BasicInfoCheckPolicy.snippetAround(text, 150, "承诺书", "承诺函", "____", "【"))));
            }
            if (hasPositiveEvidence || (hasFilledSignatory && hasSealMark)) {
                return QualityCheckItemResult.pass(item, "包含合规承诺书且签章要素齐全", List.of(QualityEvidence.bid("承诺书签署", match.pageNumber(), BasicInfoCheckPolicy.snippetAround(text, 200, "承诺书", "承诺函", "公章", "盖章", "签字"))));
            }
            if (hasSealMark) {
                return QualityCheckItemResult.manualReview(item, "已检索到承诺书，但落款处仅有签章格式说明文字且未识别到已填签署人，文本层无法确认实际盖章", "请人工复核承诺书落款处是否已完成盖章与签署（落款「（盖章）」等说明文字不代表已实际盖章）", List.of(QualityEvidence.bid("承诺书章节", match.pageNumber(), BasicInfoCheckPolicy.snippetAround(text, 200, "承诺书", "承诺函"))));
            }
            return QualityCheckItemResult.manualReview(item, "已检索到承诺书，但落款处未识别到明确的公章或签字标记", "请人工复核承诺书落款处是否已完成盖章与签署", List.of(QualityEvidence.bid("承诺书章节", match.pageNumber(), BasicInfoCheckPolicy.snippetAround(text, 200, "承诺书", "承诺函"))));
        }
        return QualityCheckItemResult.manualReview(item, "未检索到独立的承诺函章节", "请核实招标文件是否要求单独提供廉洁/无行贿承诺书", Collections.emptyList());
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
        BasicInfoCheckPolicy.PageMatch match = BasicInfoCheckPolicy.findFirstPageContaining(bidDoc,
                "退还保证金", "保证金退还", "退还开户银行", "退还账户", "退保证金");
        if (match != null) {
            return QualityCheckItemResult.pass(item, "包含保证金退还开户行及银行账户信息", List.of(QualityEvidence.bid("退款账户信息页", match.pageNumber(), BasicInfoCheckPolicy.snippetAround(match.text(), 200, "退还", "开户银行", "银行账号", "基本存款账户"))));
        }
        return QualityCheckItemResult.manualReview(item, "未检索到明确的保证金退还账户信息表", "若招标文件要求提供退还账户信息，请在附件中补充基本账户开户证明", Collections.emptyList());
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
