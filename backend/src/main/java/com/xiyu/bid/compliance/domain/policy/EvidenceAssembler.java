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
import java.util.Set;

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
     * 投标文件正文页证据定位（跳过目录索引页）：命中正文页返回真实页码 + 原文摘录，
     * 仅目录页命中或未命中时 page=null、quote 为空（诚实未定位，防目录页假定位）.
     */
    public static QualityEvidence locateBidSkippingCatalog(final DocumentContent doc, final String title, final List<String> keywords) {
        if (doc == null || doc.pages() == null) {
            return QualityEvidence.bid(title, null, "");
        }
        return EvidenceLocator.locateSkippingCatalog(doc.pages(), keywords)
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
                if (requirements != null && requirements.mandatoryQualifications() != null) {
                    for (String qual : QualificationMatchPolicy.expandCombinedIsoRequirements(requirements.certificateRequirements())) {
                        if (qual != null && !qual.isBlank() && DeterministicQualificationExtractor.isSpecificCertificate(qual)) {
                            evidences.add(locateTender(tenderDoc, "招标文件资质要求", List.of(qual)));
                            evidences.add(locateBidSkippingCatalog(bidDoc, "资质证明材料正文", List.of(qual)));
                        }
                    }
                }
                evidences.add(locateBidSkippingCatalog(bidDoc, "资质证书分卷", List.of("资质证书", "企业资质", "认证证书", "许可证", "ISO")));
                appendKnowledgeEvidences(evidences, kbEvidences, Set.of(KnowledgeMatch.SOURCE_QUALIFICATION));
            }
            case QUALIFICATION_VETO -> {
                if (requirements != null) {
                    for (QualificationCondition condition : requirements.qualificationConditions()) {
                        evidences.add(qualificationSource(tenderDoc, condition));
                    }
                }
                if (requirements != null && requirements.vetoClauses() != null) {
                    for (TenderRequirements.VetoClause veto : requirements.vetoClauses()) {
                        if (veto != null && !veto.keywords().isEmpty()) {
                            evidences.add(locateTender(tenderDoc, "招标一票否决条款", veto.keywords()));
                        }
                    }
                }
                evidences.add(locateBidSkippingCatalog(bidDoc, "一票否决与实质性响应条款", List.of("一票否决", "否决投标", "废标", "转包", "分包", "资格审查", "实质性响应")));
                appendKnowledgeEvidences(evidences, kbEvidences, Set.of(KnowledgeMatch.SOURCE_QUALIFICATION));
            }
            case FRONT_TABLE_RESPONSE -> evidences.add(locateBidSkippingCatalog(
                    bidDoc, "前附表关键条款响应", FrontTableResponsePolicy.RESPONSE_HEADINGS));
            case AUTHORIZATION_LETTER -> {
                evidences.add(locateBidSkippingCatalog(bidDoc, "授权委托书正文", List.of("授权委托书", "法定代表人授权书")));
                evidences.add(locateBidSkippingCatalog(bidDoc, "法定代表人身份证明正文", List.of("法定代表人身份证明", "法定代表人证明书")));
            }
            case ID_AUTH_LETTER -> evidences.add(locateBidSkippingCatalog(bidDoc, "身份证明附件正文",
                    List.of("法定代表人身份证明", "身份证扫描件", "身份证复印件", "公民身份号码", "居民身份证")));
            case RESPONSIVENESS_REVIEW -> evidences.add(locateBid(bidDoc, "偏离表章节", List.of("商务偏离表", "技术偏离表", "偏离表", "负偏离", "不满足")));
            case QUALIFICATION_REVIEW -> {
                evidences.add(locateBidSkippingCatalog(bidDoc, "企业信誉、纳税社保与类似业绩", List.of("信用中国", "纳税", "社保", "信誉", "业绩", "履约")));
                evidences.add(locateBidSkippingCatalog(bidDoc, "财务证明正文", List.of("审计报告", "财务报表", "资产负债表", "利润表", "现金流量表")));
                appendKnowledgeEvidences(evidences, kbEvidences, Set.of(KnowledgeMatch.SOURCE_QUALIFICATION, KnowledgeMatch.SOURCE_PERFORMANCE));
            }
            case SCORING_RESPONSE -> {
                if (requirements != null && requirements.scoringItems() != null) {
                    for (TenderRequirements.ScoringItem scoringItem : requirements.scoringItems()) {
                        evidences.add(locateTender(tenderDoc, "招标评分办法", List.of(scoringItem.name())));
                    }
                }
                evidences.add(locateBidSkippingCatalog(bidDoc, "技术方案与评分标准对应", List.of("评分标准对应表", "评分细则", "技术方案", "实施方案", "项目管理机构", "服务方案")));
            }
            case CLARIFICATION_RESPONSE -> {
                evidences.add(locateTender(tenderDoc, "招标澄清答疑要求", List.of("澄清", "答疑", "补遗")));
                evidences.add(locateBidSkippingCatalog(bidDoc, "澄清答疑确认函/响应章节", List.of("澄清文件确认函", "答疑纪要确认函", "补遗书确认函", "澄清确认函", "答疑纪要", "补遗书", "澄清")));
            }
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
                evidences.add(locateBidSkippingCatalog(bidDoc, "CA数字证书及签章凭据", List.of("CA证书", "数字证书", "电子签章", "有效性证明", "证书序列号", "CFCA")));
                appendKnowledgeEvidences(evidences, kbEvidences, Set.of(KnowledgeMatch.SOURCE_CA_CERTIFICATE));
            }
            default -> {
                // 其余项未特化证据
            }
        }
        return evidences;
    }

    private static QualityEvidence qualificationSource(final DocumentContent document, final QualificationCondition condition) {
        var source = condition.source();
        if (document != null && source.pageNumber() != null && !source.quote().isBlank()
                && source.fileHash().equals(document.fileHash()) && source.fileName().equals(document.fileName())
                && document.pages().stream().anyMatch(p -> p.pageNumber() == source.pageNumber() && p.text().contains(source.quote()))) {
            return QualityEvidence.tender("招标文件完整资格条件", source.pageNumber(), source.quote());
        }
        return locateTender(document, "招标文件资格条件（来源待定位）", List.of(condition.text()));
    }

    /** 附加知识库证据（上限 {@link #MAX_KB_EVIDENCES}，按白名单源过滤）并返回合并后的证据列表. */
    private static List<QualityEvidence> appendKnowledgeEvidences(
            final List<QualityEvidence> target, final List<QualityEvidence> kbEvidences, final Set<String> allowedSources) {
        if (kbEvidences == null) {
            return target;
        }
        target.addAll(kbEvidences.stream()
                .filter(e -> allowedSources == null || allowedSources.isEmpty()
                        || (e.knowledgeSource() != null && allowedSources.contains(e.knowledgeSource())))
                .limit(MAX_KB_EVIDENCES)
                .toList());
        return target;
    }
}
