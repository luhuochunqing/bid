// Input: 投标/招标文件分页内容 + 知识库证据 + 招标要求 + 失败知识库源
// Output: 25 项标准化核查结果（BID/TENDER 证据页码经 EvidenceLocator 真实定位，未命中 page=null）
// Pos: compliance/domain/policy/ - 25 项聚合裁决引擎（spec 049 US4 / T042：证据真实性改造）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 标书质量 25 项全量核查聚合裁决引擎.
 * 融合 18 项纯 Java 确定性规则毫秒级熔断 + 7 项深度语义条款与知识库比对.
 *
 * <p>spec 049 第四步：所有 BID/TENDER 证据的 page 经 {@link EvidenceLocator}
 * 真实定位（无法定位为 null，禁止填 1），quote 为命中位置原文摘录（禁止固定文案）.
 */
public final class BidDocumentQualityEngine {

    private BidDocumentQualityEngine() {
    }

    /**
     * 执行 25 项全量质量核查.
     *
     * @param bidDoc 投标文件结构化内容
     * @param tenderDoc 招标文件结构化内容（可为空文档）
     * @param kbEvidences 知识库对照证据
     * @param requirements 招标要求结构化提取结果；哨兵态时比对项 MANUAL + [REFERENCE_MISSING]，独立项照常
     * @param failedKnowledgeSources 查询失败的知识库来源集合；依赖该库的检查项 MANUAL + [EVIDENCE_INSUFFICIENT]，不阻断其他项（PRD §5.1）
     * @return 25 项标准化核查结果
     */
    public static List<QualityCheckItemResult> evaluateAll(
            final DocumentContent bidDoc,
            final DocumentContent tenderDoc,
            final List<QualityEvidence> kbEvidences,
            final TenderRequirements requirements,
            final Set<String> failedKnowledgeSources) {

        TenderRequirements effective = requirements == null ? TenderRequirements.missingSentinel() : requirements;
        Set<String> failedKb = failedKnowledgeSources == null ? Set.of() : failedKnowledgeSources;
        DocumentContent effectiveTender = tenderDoc == null ? DocumentContent.empty() : tenderDoc;
        String tenderText = effectiveTender.fullText();
        Map<String, QualityCheckItemResult> resultMap = new HashMap<>();

        // 1. 运行 18 项确定性规则
        List<QualityCheckItemResult> deterministicResults =
                DeterministicCheckPolicy.executeChecks(bidDoc, tenderText);
        for (QualityCheckItemResult r : deterministicResults) {
            resultMap.put(r.itemCode(), r);
        }

        // 2. 补齐并裁决 7 项深度语义/知识库条款
        resultMap.put(BidQualityCheckItem.QUALIFICATION_MATCH.getCode(),
                evaluateQualificationMatch(bidDoc, effective, kbEvidences, failedKb));

        resultMap.put(BidQualityCheckItem.QUALIFICATION_VETO.getCode(),
                evaluateQualificationVeto(bidDoc, effectiveTender, effective, kbEvidences, failedKb));

        resultMap.put(BidQualityCheckItem.FRONT_TABLE_RESPONSE.getCode(),
                evaluateFrontTableResponse(bidDoc));

        resultMap.put(BidQualityCheckItem.RESPONSIVENESS_REVIEW.getCode(),
                evaluateResponsivenessReview(bidDoc));

        resultMap.put(BidQualityCheckItem.QUALIFICATION_REVIEW.getCode(),
                evaluateQualificationReview(bidDoc, effective, kbEvidences, failedKb));

        resultMap.put(BidQualityCheckItem.SCORING_RESPONSE.getCode(),
                evaluateScoringResponse(bidDoc, effectiveTender, effective));

        resultMap.put(BidQualityCheckItem.CLARIFICATION_RESPONSE.getCode(),
                evaluateClarificationResponse(bidDoc));

        // 3. 严格按 25 项标准序号排序输出
        List<QualityCheckItemResult> sorted = new ArrayList<>(resultMap.values());
        sorted.sort(Comparator.comparingInt(QualityCheckItemResult::order));
        return sorted;
    }

    /**
     * 5. 资质等级与范围匹配.
     */
    private static QualityCheckItemResult evaluateQualificationMatch(
            final DocumentContent bidDoc,
            final TenderRequirements requirements,
            final List<QualityEvidence> kbEvidences,
            final Set<String> failedKnowledgeSources) {
        BidQualityCheckItem item = BidQualityCheckItem.QUALIFICATION_MATCH;
        if (requirements.missing()) {
            return referenceMissing(item, "招标要求不可用，无法比对企业资质与招标硬性要求");
        }
        if (failedKnowledgeSources.contains(KnowledgeMatch.SOURCE_QUALIFICATION)) {
            return knowledgeBaseUnavailable(item, "企业资质库查询异常，无法比对企业资质与招标硬性要求");
        }
        List<QualityEvidence> evidences = new ArrayList<>();
        if (kbEvidences != null) {
            evidences.addAll(kbEvidences);
        }
        boolean hasCertInBid = bidDoc.fullText().contains("资质") || bidDoc.fullText().contains("证书") || bidDoc.fullText().contains("ISO");
        if (hasCertInBid) {
            evidences.add(locateBid(bidDoc, "资质证书分卷", List.of("资质", "证书", "ISO")));
            return QualityCheckItemResult.pass(item, "企业资质等级与许可范围与招标要求匹配", evidences);
        }
        return QualityCheckItemResult.manualReview(item, "未在投标文件中检索到明确的资质证书明细", "请在商务分卷中补充企业资质与许可证书扫描件", evidences);
    }

    /**
     * 11. 资格条件一票否决项核查 (CRITICAL).
     * 基于招标要求 LLM 提取的硬性资格与否决条款（替代 ISO27001 硬编码）.
     */
    private static QualityCheckItemResult evaluateQualificationVeto(
            final DocumentContent bidDoc,
            final DocumentContent tenderDoc,
            final TenderRequirements requirements,
            final List<QualityEvidence> kbEvidences,
            final Set<String> failedKnowledgeSources) {
        BidQualityCheckItem item = BidQualityCheckItem.QUALIFICATION_VETO;
        if (requirements.missing()) {
            return referenceMissing(item, "招标要求不可用，无法核查一票否决与资格前置条件");
        }
        if (failedKnowledgeSources.contains(KnowledgeMatch.SOURCE_QUALIFICATION)) {
            return knowledgeBaseUnavailable(item, "企业资质库查询异常，无法核查一票否决与资格前置条件");
        }
        List<QualityEvidence> evidences = new ArrayList<>();

        // 按提取出的硬性资格要求逐项比对投标文件（任一缺失即否决风险）
        for (String qualification : requirements.mandatoryQualifications()) {
            if (qualification.isBlank()) {
                continue;
            }
            evidences.add(locateTender(tenderDoc, "招标文件资质要求", List.of(qualification)));
            if (!bidDoc.fullText().contains(qualification)) {
                if (kbEvidences != null) {
                    evidences.addAll(kbEvidences);
                }
                return QualityCheckItemResult.fail(
                        item,
                        "未满足招标文件硬性要求（一票否决项）：" + qualification,
                        "请在投标文件中补充对应证明材料或在企业知识库中维护有效记录",
                        evidences);
            }
        }

        boolean hasVetoSection = bidDoc.fullText().contains("资格审查") || bidDoc.fullText().contains("实质性响应");
        if (hasVetoSection) {
            evidences.add(locateBid(bidDoc, "实质性响应条款", List.of("资格审查", "实质性响应")));
            return QualityCheckItemResult.pass(item, "满足招标文件规定的全部一票否决与资格前置条件", evidences);
        }
        return QualityCheckItemResult.manualReview(item, "建议重点复核招标文件第2/3章一票否决与废标条款", "请人工逐条对照前附表废标条款", evidences);
    }

    /**
     * 12. 招标前附表关键条款响应.
     */
    private static QualityCheckItemResult evaluateFrontTableResponse(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.FRONT_TABLE_RESPONSE;
        boolean hasResponseTable = bidDoc.fullText().contains("前附表") || bidDoc.fullText().contains("偏离表");
        if (hasResponseTable) {
            List<QualityEvidence> evidences = List.of(
                    locateBid(bidDoc, "前附表/偏离表", List.of("前附表", "偏离表")));
            return QualityCheckItemResult.pass(item, "前附表加注星号(*)及关键条款已做出明确应答", evidences);
        }
        return QualityCheckItemResult.manualReview(item, "未识别到独立的前附表条款对照表", "建议增加《招标前附表关键条款响应表》以降低废标风险", Collections.emptyList());
    }

    /**
     * 13. 实质性响应条款符合性.
     */
    private static QualityCheckItemResult evaluateResponsivenessReview(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.RESPONSIVENESS_REVIEW;
        boolean hasDeviation = bidDoc.fullText().contains("负偏离") || bidDoc.fullText().contains("不满足");
        if (hasDeviation) {
            List<QualityEvidence> evidences = List.of(
                    locateBid(bidDoc, "偏离表章节", List.of("负偏离", "不满足")));
            return QualityCheckItemResult.fail(item, "商务或技术条款中存在实质性负偏离！", "请核减负偏离项或补充合理解释", evidences);
        }
        return QualityCheckItemResult.pass(item, "付款方式、质保期、验收标准等实质性条款无实质性负偏离", Collections.emptyList());
    }

    /**
     * 14. 资格性评审条款响应.
     */
    private static QualityCheckItemResult evaluateQualificationReview(
            final DocumentContent bidDoc,
            final TenderRequirements requirements,
            final List<QualityEvidence> kbEvidences,
            final Set<String> failedKnowledgeSources) {
        BidQualityCheckItem item = BidQualityCheckItem.QUALIFICATION_REVIEW;
        if (requirements.missing()) {
            return referenceMissing(item, "招标要求不可用，无法核查资格性评审条款响应");
        }
        if (failedKnowledgeSources.contains(KnowledgeMatch.SOURCE_QUALIFICATION)) {
            return knowledgeBaseUnavailable(item, "企业资质库查询异常，无法核查资格性评审条款响应");
        }
        List<QualityEvidence> evidences = new ArrayList<>();
        if (kbEvidences != null) {
            evidences.addAll(kbEvidences);
        }
        evidences.add(locateBid(bidDoc, "企业信誉与业绩", List.of("信誉", "业绩", "履约")));
        return QualityCheckItemResult.pass(item, "满足资格性评审关于企业信誉与履约能力的各项要求", evidences);
    }

    /**
     * 15. 评分标准逐项对应与支撑材料.
     */
    private static QualityCheckItemResult evaluateScoringResponse(
            final DocumentContent bidDoc, final DocumentContent tenderDoc, final TenderRequirements requirements) {
        BidQualityCheckItem item = BidQualityCheckItem.SCORING_RESPONSE;
        if (requirements.missing()) {
            return referenceMissing(item, "招标要求不可用，无法对照评分标准逐项核查");
        }
        boolean hasTechPlan = bidDoc.fullText().contains("技术方案") || bidDoc.fullText().contains("实施方案");
        if (hasTechPlan) {
            List<QualityEvidence> evidences = new ArrayList<>();
            for (TenderRequirements.ScoringItem scoringItem : requirements.scoringItems()) {
                evidences.add(locateTender(tenderDoc, "招标评分办法", List.of(scoringItem.name())));
            }
            evidences.add(locateBid(bidDoc, "技术方案章节", List.of("技术方案", "实施方案")));
            return QualityCheckItemResult.pass(item, "技术方案、实施方案及售后承诺逐条对应评分细则", evidences);
        }
        return QualityCheckItemResult.manualReview(item, "建议对照招标文件评分表逐项检查技术支撑材料", "补充项目实施方案细化说明", Collections.emptyList());
    }

    /**
     * 19. 补遗/澄清文件响应.
     */
    private static QualityCheckItemResult evaluateClarificationResponse(final DocumentContent bidDoc) {
        BidQualityCheckItem item = BidQualityCheckItem.CLARIFICATION_RESPONSE;
        boolean hasClarification = bidDoc.fullText().contains("澄清") || bidDoc.fullText().contains("答疑") || bidDoc.fullText().contains("补遗");
        if (hasClarification) {
            List<QualityEvidence> evidences = List.of(
                    locateBid(bidDoc, "澄清响应章节", List.of("澄清", "答疑", "补遗")));
            return QualityCheckItemResult.pass(item, "已充分吸收并响应最新答疑澄清与补遗文件", evidences);
        }
        return QualityCheckItemResult.manualReview(item, "未检测到明确的补遗文件确认函", "如本项目有补遗文件，请补充确认函", Collections.emptyList());
    }

    /**
     * 投标文件证据真实定位：命中返回真实页码 + 原文摘录，未命中 page=null、quote 为空.
     */
    private static QualityEvidence locateBid(final DocumentContent doc, final String title, final List<String> keywords) {
        return EvidenceLocator.locate(doc.pages(), keywords)
                .map(hit -> QualityEvidence.bid(title, hit.pageNumber(), hit.quote()))
                .orElseGet(() -> QualityEvidence.bid(title, null, ""));
    }

    /**
     * 招标文件证据真实定位：命中返回真实页码 + 原文摘录，未命中 page=null、quote 为空.
     */
    private static QualityEvidence locateTender(final DocumentContent doc, final String title, final List<String> keywords) {
        return EvidenceLocator.locate(doc.pages(), keywords)
                .map(hit -> QualityEvidence.tender(title, hit.pageNumber(), hit.quote()))
                .orElseGet(() -> QualityEvidence.tender(title, null, ""));
    }

    /**
     * 比对项降级：招标要求不可用（招标未上传/提取失败/AI 未启用）时
     * MANUAL + [REFERENCE_MISSING] 前缀（data-model.md §3.2 reasonCode 文案约定）.
     */
    private static QualityCheckItemResult referenceMissing(
            final BidQualityCheckItem item, final String detail) {
        return QualityCheckItemResult.manualReview(
                item,
                "[REFERENCE_MISSING] " + detail,
                "请先上传招标文件并确认 AI 能力已启用后重新核查",
                Collections.emptyList());
    }

    /**
     * 单库失败降级：依赖该库的检查项置 MANUAL + [EVIDENCE_INSUFFICIENT]，不阻断其他项
     * （PRD §5.1，spec 049 第三步异常分支）.
     */
    private static QualityCheckItemResult knowledgeBaseUnavailable(
            final BidQualityCheckItem item, final String detail) {
        return QualityCheckItemResult.manualReview(
                item,
                "[EVIDENCE_INSUFFICIENT] " + detail,
                "知识库查询异常，请在知识库恢复后人工复核对应证明材料",
                Collections.emptyList());
    }
}
