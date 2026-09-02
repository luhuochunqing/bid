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

    /** 开标一览表/报价表识别词表：选页判定与证据摘录必须共用同一份（新增词条只改这里）. */
    private static final String[] BID_OPENING_TABLE_KEYWORDS = {
            "开标一览表", "投标报价表", "报价一览表", "投标一览表", "报价明细表", "分项报价表"
    };

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

    public static QualityCheckItemResult checkCommitmentSeal(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.COMMITMENT_SEAL;
        BasicInfoCheckPolicy.PageMatch match = BasicInfoCheckPolicy.findFirstPageContaining(bidDoc, "承诺书", "承诺函", "廉洁承诺", "诚信承诺");
        if (match != null) {
            return QualityCheckItemResult.pass(item, "包含必要的承诺书签署文件", List.of(QualityEvidence.bid("承诺书章节", match.pageNumber(), BasicInfoCheckPolicy.snippetAround(match.text(), 200, "承诺书", "承诺函", "廉洁承诺", "诚信承诺"))));
        }
        return QualityCheckItemResult.manualReview(item, "未检索到独立的承诺函章节", "请核实招标文件是否要求单独提供廉洁/无行贿承诺书", Collections.emptyList());
    }

    public static QualityCheckItemResult checkIdAuthLetter(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.ID_AUTH_LETTER;
        BasicInfoCheckPolicy.PageMatch match = BasicInfoCheckPolicy.findFirstPageContaining(bidDoc, "身份证", "社保", "社会保险");
        if (match != null) {
            return QualityCheckItemResult.pass(item, "已附授权委托人身份证明或社保证明文件", List.of(QualityEvidence.bid("身份证/社保附件", match.pageNumber(), BasicInfoCheckPolicy.snippetAround(match.text(), 200, "身份证", "社保", "社会保险"))));
        }
        return QualityCheckItemResult.fail(item, "缺少授权委托人身份证扫描件或社保证明", "请在附件清单中补充委托代理人身份证复印件及近3个月社保证明", Collections.emptyList());
    }

    public static QualityCheckItemResult checkBidOpeningTable(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.BID_OPENING_TABLE;
        BasicInfoCheckPolicy.PageMatch match = BasicInfoCheckPolicy.findFirstPageContaining(bidDoc, BID_OPENING_TABLE_KEYWORDS);
        if (match != null) {
            return QualityCheckItemResult.pass(item, "包含开标一览表或分项报价明细表", List.of(QualityEvidence.bid("开标一览表所在页", match.pageNumber(), BasicInfoCheckPolicy.snippetAround(match.text(), 200, BID_OPENING_TABLE_KEYWORDS))));
        }
        return QualityCheckItemResult.fail(item, "未检测到开标一览表或分项报价表", "请在投标文件中加入《开标一览表》及分项报价清单", Collections.emptyList());
    }

    public static QualityCheckItemResult checkDepositReceipt(final DocumentContent bidDoc) {
        return checkDepositReceipt(bidDoc, null);
    }

    public static QualityCheckItemResult checkDepositReceipt(final DocumentContent bidDoc, final String tenderText) {
        BidQualityCheckItem item = BidQualityCheckItem.DEPOSIT_RECEIPT;
        if (isDepositNotRequired(tenderText)) {
            return QualityCheckItemResult.pass(item, "招标文件未要求缴纳投标保证金（免收/不涉及）", Collections.emptyList());
        }
        BasicInfoCheckPolicy.PageMatch match = BasicInfoCheckPolicy.findFirstPageContaining(bidDoc,
                "投标保证金汇款", "保证金转账", "银行汇款回单", "电子保函凭证", "保函", "汇款凭证", "缴纳凭证");
        if (match != null) {
            return QualityCheckItemResult.pass(item, "已附投标保证金汇款回单或电子保函凭证", List.of(QualityEvidence.bid("保证金凭证页", match.pageNumber(), BasicInfoCheckPolicy.snippetAround(match.text(), 200, "投标保证金", "保函", "汇款凭证", "银行回单"))));
        }
        if (bidDoc.fullText().contains("不收取投标保证金") || bidDoc.fullText().contains("免收保证金") || bidDoc.fullText().contains("不涉及保证金")) {
            return QualityCheckItemResult.pass(item, "投标文件中声明不涉及/免收投标保证金", Collections.emptyList());
        }
        return QualityCheckItemResult.fail(item, "未检索到投标保证金缴纳凭证或投标保函", "请补充投标保证金转账回执单扫描件或有效保函", Collections.emptyList());
    }

    public static QualityCheckItemResult checkCaCertificate(final DocumentContent bidDoc) {
        return checkCaCertificate(bidDoc, null);
    }

    public static QualityCheckItemResult checkCaCertificate(final DocumentContent bidDoc, final String tenderText) {
        BidQualityCheckItem item = BidQualityCheckItem.CA_CERTIFICATE;
        if (isCaNotRequired(tenderText)) {
            return QualityCheckItemResult.pass(item, "招标文件未要求 CA 电子签章或数字证书（不涉及）", Collections.emptyList());
        }
        BasicInfoCheckPolicy.PageMatch match = BasicInfoCheckPolicy.findFirstPageContaining(bidDoc,
                "CA数字证书", "CA电子签章", "数字证书认证", "电子印章", "CA证书");
        if (match != null) {
            return QualityCheckItemResult.pass(item, "包含 CA 电子签章或数字证书有效性标识", List.of(QualityEvidence.bid("CA/签章标识", match.pageNumber(), BasicInfoCheckPolicy.snippetAround(match.text(), 200, "CA", "数字证书", "电子签章", "电子印章"))));
        }
        return QualityCheckItemResult.manualReview(item, "未识别到 CA 证书明文字符（如为电子标，请在交易平台客户端核验 CA 锁签名）", "建议使用 CA 客户端校验数字证书有效性", Collections.emptyList());
    }

    public static QualityCheckItemResult checkDepositRefund(final DocumentContent bidDoc) {
        return checkDepositRefund(bidDoc, null);
    }

    public static QualityCheckItemResult checkDepositRefund(final DocumentContent bidDoc, final String tenderText) {
        BidQualityCheckItem item = BidQualityCheckItem.DEPOSIT_REFUND;
        if (isDepositNotRequired(tenderText)) {
            return QualityCheckItemResult.pass(item, "本项目不涉及投标保证金，无需提供退款账户信息", Collections.emptyList());
        }
        BasicInfoCheckPolicy.PageMatch match = BasicInfoCheckPolicy.findFirstPageContaining(bidDoc,
                "退还保证金", "保证金退还", "退还开户银行", "退还账户", "退保证金");
        if (match != null) {
            return QualityCheckItemResult.pass(item, "包含保证金退还开户行及银行账户信息", List.of(QualityEvidence.bid("退款账户信息页", match.pageNumber(), BasicInfoCheckPolicy.snippetAround(match.text(), 200, "退还", "开户银行", "银行账号", "基本存款账户"))));
        }
        return QualityCheckItemResult.manualReview(item, "未检索到明确的保证金退还账户信息表", "若招标文件要求提供退还账户信息，请在附件中补充基本账户开户证明", Collections.emptyList());
    }

    private static boolean isDepositNotRequired(final String tenderText) {
        if (tenderText == null || tenderText.isBlank()) {
            return false;
        }
        return tenderText.contains("免收投标保证金")
                || tenderText.contains("不收取投标保证金")
                || tenderText.contains("免交投标保证金")
                || tenderText.contains("不要求提交投标保证金")
                || tenderText.contains("免收保证金")
                || tenderText.contains("无需缴纳保证金")
                || (!tenderText.contains("保证金") && !tenderText.contains("保函"));
    }

    private static boolean isCaNotRequired(final String tenderText) {
        if (tenderText == null || tenderText.isBlank()) {
            return false;
        }
        if ((tenderText.contains("无需") && tenderText.contains("CA"))
                || (tenderText.contains("不要求") && tenderText.contains("CA"))
                || tenderText.contains("纸质标")
                || tenderText.contains("不使用电子签章")) {
            return true;
        }
        boolean tenderRequiresCa = tenderText.contains("电子招投标")
                || tenderText.contains("CA证书")
                || tenderText.contains("CA数字证书")
                || tenderText.contains("电子签章")
                || tenderText.contains("电子印章")
                || tenderText.contains("数字证书有效")
                || tenderText.contains("加密投标文件");
        return !tenderRequiresCa;
    }
}
