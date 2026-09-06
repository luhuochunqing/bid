// Input: 投标/招标分页文档 + 证据标题 + 关键词线索（+ 知识库证据）
// Output: 各语义项的真实定位证据列表（未命中 page=null、quote 为空，禁止填 1）
// Pos: compliance/domain/policy/ - 语义项证据组装（spec 049 US5 / T046 拆分产物，research.md R5/R6）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义项证据组装（纯函数）.
 *
 * <p>所有 BID/TENDER 证据经 {@link EvidenceLocator} 真实定位；
 * 无法定位时 page=null、quote 为空，由前端渲染"未定位"兜底态.
 */
public final class EvidenceAssembler {

    /** 知识库证据附加上限（避免证据面板过长）. */
    private static final int MAX_KB_EVIDENCES = 5;

    private EvidenceAssembler() {
    }

    /**
     * 投标文件证据真实定位：命中返回真实页码 + 原文摘录，未命中 page=null、quote 为空.
     */
    public static QualityEvidence locateBid(final DocumentContent doc, final String title, final List<String> keywords) {
        return EvidenceLocator.locate(doc.pages(), keywords)
                .map(hit -> QualityEvidence.bid(title, hit.pageNumber(), hit.quote()))
                .orElseGet(() -> QualityEvidence.bid(title, null, ""));
    }

    /**
     * 招标文件证据真实定位：命中返回真实页码 + 原文摘录，未命中 page=null、quote 为空.
     */
    public static QualityEvidence locateTender(final DocumentContent doc, final String title, final List<String> keywords) {
        return EvidenceLocator.locate(doc.pages(), keywords)
                .map(hit -> QualityEvidence.tender(title, hit.pageNumber(), hit.quote()))
                .orElseGet(() -> QualityEvidence.tender(title, null, ""));
    }

    /**
     * 按语义项组装裁决证据（LLM lane 与规则 lane 共用）.
     *
     * @param item 语义检查项
     * @param bidDoc 投标文件
     * @param tenderDoc 招标文件
     * @param requirements 招标要求（评分项/硬性要求作为招标侧定位线索）
     * @param kbEvidences 知识库对照证据（资质相关项附加）
     */
    public static List<QualityEvidence> semanticEvidences(
            final BidQualityCheckItem item,
            final DocumentContent bidDoc,
            final DocumentContent tenderDoc,
            final TenderRequirements requirements,
            final List<QualityEvidence> kbEvidences) {
        List<QualityEvidence> evidences = new ArrayList<>();
        switch (item) {
            case QUALIFICATION_MATCH -> {
                evidences.add(locateBid(bidDoc, "资质证书分卷", List.of("资质", "证书", "ISO")));
                appendKnowledgeEvidences(evidences, kbEvidences);
            }
            case QUALIFICATION_VETO -> {
                for (String qualification : requirements.mandatoryQualifications()) {
                    if (!qualification.isBlank()) {
                        evidences.add(locateTender(tenderDoc, "招标文件资质要求", List.of(qualification)));
                    }
                }
                evidences.add(locateBid(bidDoc, "实质性响应条款", List.of("资格审查", "实质性响应")));
                appendKnowledgeEvidences(evidences, kbEvidences);
            }
            case FRONT_TABLE_RESPONSE -> evidences.add(locateBid(bidDoc, "前附表/偏离表", List.of("前附表", "偏离表")));
            case RESPONSIVENESS_REVIEW -> evidences.add(locateBid(bidDoc, "偏离表章节", List.of("负偏离", "不满足")));
            case QUALIFICATION_REVIEW -> {
                evidences.add(locateBid(bidDoc, "企业信誉与业绩", List.of("信誉", "业绩", "履约")));
                appendKnowledgeEvidences(evidences, kbEvidences);
            }
            case SCORING_RESPONSE -> {
                for (TenderRequirements.ScoringItem scoringItem : requirements.scoringItems()) {
                    evidences.add(locateTender(tenderDoc, "招标评分办法", List.of(scoringItem.name())));
                }
                evidences.add(locateBid(bidDoc, "技术方案章节", List.of("技术方案", "实施方案")));
            }
            case CLARIFICATION_RESPONSE -> evidences.add(locateBid(bidDoc, "澄清响应章节", List.of("澄清", "答疑", "补遗")));
            case DEPOSIT_RECEIPT -> {
                evidences.add(locateBid(bidDoc, "投标保证金凭证", List.of("投标保证金", "银行回单", "电子保函", "转账凭证")));
            }
            case BID_LETTER_FORMAT -> {
                if (requirements != null && requirements.bidLetterRequirement() != null
                        && requirements.bidLetterRequirement().description() != null) {
                    evidences.add(locateTender(tenderDoc, "招标投标函要求", List.of("投标函", "报价函", "响应函", "致采购人", "致招标人")));
                }
                evidences.add(locateBid(bidDoc, "投标函/报价函正文", List.of("投标函", "报价函", "响应函", "致：", "致:")));
            }
            case BID_OPENING_TABLE -> {
                evidences.add(locateBid(bidDoc, "开标一览表/报价表正文", List.of("开标一览表", "报价一览表", "分项报价表", "报价明细表", "投标总价")));
            }
            case PRICE_CONSISTENCY -> {
                if (requirements != null && requirements.priceRequirement() != null
                        && (requirements.priceRequirement().maxPrice() != null || requirements.priceRequirement().maxPriceRawText() != null)) {
                    evidences.add(locateTender(tenderDoc, "招标最高限价条款", List.of("最高投标限价", "招标控制价", "最高限价", "采购预算")));
                }
                evidences.add(locateBid(bidDoc, "投标报价与大小写明细", List.of("投标总价", "投标报价", "大写", "小写", "开标一览表")));
            }
            case CA_CERTIFICATE -> {
                if (requirements != null && requirements.caCertificateRequirement() != null
                        && requirements.caCertificateRequirement().description() != null) {
                    evidences.add(locateTender(tenderDoc, "招标CA数字证书要求", List.of("CA", "数字证书", "电子签章", "电子交易")));
                }
                evidences.add(locateBid(bidDoc, "CA数字证书及签章凭据", List.of("CA证书", "数字证书", "电子签章", "有效性证明", "证书序列号", "CFCA")));
            }
            default -> {
                // 其余项未特化证据
            }
        }
        return evidences;
    }

    /** 附加知识库证据（上限 {@link #MAX_KB_EVIDENCES}）并返回合并后的证据列表. */
    private static List<QualityEvidence> appendKnowledgeEvidences(
            final List<QualityEvidence> target, final List<QualityEvidence> kbEvidences) {
        if (kbEvidences == null) {
            return target;
        }
        target.addAll(kbEvidences.stream().limit(MAX_KB_EVIDENCES).toList());
        return target;
    }
}
