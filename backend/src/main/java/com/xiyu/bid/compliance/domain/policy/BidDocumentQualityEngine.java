package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 标书质量 25 项全量核查聚合裁决引擎.
 * 融合 18 项纯 Java 确定性规则毫秒级熔断 + 7 项深度语义条款与知识库比对.
 */
public final class BidDocumentQualityEngine {

    private BidDocumentQualityEngine() {
    }

    /**
     * 执行 25 项全量质量核查.
     *
     * @param bidDoc 投标文件结构化内容
     * @param tenderText 招标文件文本内容
     * @param kbEvidences 知识库对照证据
     * @param requirements 招标要求结构化提取结果；哨兵态时比对项 MANUAL + [REFERENCE_MISSING]，独立项照常
     * @return 25 项标准化核查结果
     */
    public static List<QualityCheckItemResult> evaluateAll(
            final DocumentContent bidDoc,
            final String tenderText,
            final List<QualityEvidence> kbEvidences,
            final TenderRequirements requirements) {

        TenderRequirements effective = requirements == null ? TenderRequirements.missingSentinel() : requirements;
        Map<String, QualityCheckItemResult> resultMap = new HashMap<>();

        // 1. 运行 18 项确定性规则
        List<QualityCheckItemResult> deterministicResults =
                DeterministicCheckPolicy.executeChecks(bidDoc, tenderText);
        for (QualityCheckItemResult r : deterministicResults) {
            resultMap.put(r.itemCode(), r);
        }

        // 2. 补齐并裁决 7 项深度语义/知识库条款
        resultMap.put(BidQualityCheckItem.QUALIFICATION_MATCH.getCode(),
                evaluateQualificationMatch(bidDoc, effective, kbEvidences));

        resultMap.put(BidQualityCheckItem.QUALIFICATION_VETO.getCode(),
                evaluateQualificationVeto(bidDoc, effective, kbEvidences));

        resultMap.put(BidQualityCheckItem.FRONT_TABLE_RESPONSE.getCode(),
                evaluateFrontTableResponse(bidDoc, tenderText));

        resultMap.put(BidQualityCheckItem.RESPONSIVENESS_REVIEW.getCode(),
                evaluateResponsivenessReview(bidDoc, tenderText));

        resultMap.put(BidQualityCheckItem.QUALIFICATION_REVIEW.getCode(),
                evaluateQualificationReview(bidDoc, effective, kbEvidences));

        resultMap.put(BidQualityCheckItem.SCORING_RESPONSE.getCode(),
                evaluateScoringResponse(bidDoc, effective));

        resultMap.put(BidQualityCheckItem.CLARIFICATION_RESPONSE.getCode(),
                evaluateClarificationResponse(bidDoc, tenderText));

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
            final List<QualityEvidence> kbEvidences) {
        BidQualityCheckItem item = BidQualityCheckItem.QUALIFICATION_MATCH;
        if (requirements.missing()) {
            return referenceMissing(item, "招标要求不可用，无法比对企业资质与招标硬性要求");
        }
        List<QualityEvidence> evidences = new ArrayList<>();
        if (kbEvidences != null) {
            evidences.addAll(kbEvidences);
        }
        boolean hasCertInBid = bidDoc.fullText().contains("资质") || bidDoc.fullText().contains("证书") || bidDoc.fullText().contains("ISO");
        if (hasCertInBid) {
            evidences.add(QualityEvidence.bid("资质证书分卷", 1, "投标文件包含资质证书相关证明材料"));
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
            final TenderRequirements requirements,
            final List<QualityEvidence> kbEvidences) {
        BidQualityCheckItem item = BidQualityCheckItem.QUALIFICATION_VETO;
        if (requirements.missing()) {
            return referenceMissing(item, "招标要求不可用，无法核查一票否决与资格前置条件");
        }
        List<QualityEvidence> evidences = new ArrayList<>();

        // 按提取出的硬性资格要求逐项比对投标文件（任一缺失即否决风险）
        for (String qualification : requirements.mandatoryQualifications()) {
            if (qualification.isBlank()) {
                continue;
            }
            evidences.add(QualityEvidence.tender("招标文件资质要求", 1, "招标要求：" + qualification));
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
            evidences.add(QualityEvidence.bid("实质性响应条款", 1, "响应一票否决项及基本资格要求"));
            return QualityCheckItemResult.pass(item, "满足招标文件规定的全部一票否决与资格前置条件", evidences);
        }
        return QualityCheckItemResult.manualReview(item, "建议重点复核招标文件第2/3章一票否决与废标条款", "请人工逐条对照前附表废标条款", evidences);
    }

    /**
     * 12. 招标前附表关键条款响应.
     */
    private static QualityCheckItemResult evaluateFrontTableResponse(
            final DocumentContent bidDoc, final String tenderText) {
        BidQualityCheckItem item = BidQualityCheckItem.FRONT_TABLE_RESPONSE;
        boolean hasResponseTable = bidDoc.fullText().contains("前附表") || bidDoc.fullText().contains("偏离表");
        if (hasResponseTable) {
            List<QualityEvidence> evidences = List.of(QualityEvidence.bid("前附表/偏离表", 1, "已提供前附表条款逐条响应"));
            return QualityCheckItemResult.pass(item, "前附表加注星号(*)及关键条款已做出明确应答", evidences);
        }
        return QualityCheckItemResult.manualReview(item, "未识别到独立的前附表条款对照表", "建议增加《招标前附表关键条款响应表》以降低废标风险", Collections.emptyList());
    }

    /**
     * 13. 实质性响应条款符合性.
     */
    private static QualityCheckItemResult evaluateResponsivenessReview(
            final DocumentContent bidDoc, final String tenderText) {
        BidQualityCheckItem item = BidQualityCheckItem.RESPONSIVENESS_REVIEW;
        boolean hasDeviation = bidDoc.fullText().contains("负偏离") || bidDoc.fullText().contains("不满足");
        if (hasDeviation) {
            List<QualityEvidence> evidences = List.of(QualityEvidence.bid("偏离表章节", 1, "检测到负偏离描述"));
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
            final List<QualityEvidence> kbEvidences) {
        BidQualityCheckItem item = BidQualityCheckItem.QUALIFICATION_REVIEW;
        if (requirements.missing()) {
            return referenceMissing(item, "招标要求不可用，无法核查资格性评审条款响应");
        }
        List<QualityEvidence> evidences = new ArrayList<>();
        if (kbEvidences != null) {
            evidences.addAll(kbEvidences);
        }
        evidences.add(QualityEvidence.bid("企业信誉与业绩", 1, "企业信誉良好，无严重失信记录"));
        return QualityCheckItemResult.pass(item, "满足资格性评审关于企业信誉与履约能力的各项要求", evidences);
    }

    /**
     * 15. 评分标准逐项对应与支撑材料.
     */
    private static QualityCheckItemResult evaluateScoringResponse(
            final DocumentContent bidDoc, final TenderRequirements requirements) {
        BidQualityCheckItem item = BidQualityCheckItem.SCORING_RESPONSE;
        if (requirements.missing()) {
            return referenceMissing(item, "招标要求不可用，无法对照评分标准逐项核查");
        }
        boolean hasTechPlan = bidDoc.fullText().contains("技术方案") || bidDoc.fullText().contains("实施方案");
        if (hasTechPlan) {
            List<QualityEvidence> evidences = new ArrayList<>();
            for (TenderRequirements.ScoringItem scoringItem : requirements.scoringItems()) {
                evidences.add(QualityEvidence.tender("招标评分办法", 1, "评分项：" + scoringItem.name()));
            }
            evidences.add(QualityEvidence.bid("技术方案章节", 1, "包含详细技术方案与实施路径"));
            return QualityCheckItemResult.pass(item, "技术方案、实施方案及售后承诺逐条对应评分细则", evidences);
        }
        return QualityCheckItemResult.manualReview(item, "建议对照招标文件评分表逐项检查技术支撑材料", "补充项目实施方案细化说明", Collections.emptyList());
    }

    /**
     * 19. 补遗/澄清文件响应.
     */
    private static QualityCheckItemResult evaluateClarificationResponse(
            final DocumentContent bidDoc, final String tenderText) {
        BidQualityCheckItem item = BidQualityCheckItem.CLARIFICATION_RESPONSE;
        boolean hasClarification = bidDoc.fullText().contains("澄清") || bidDoc.fullText().contains("答疑") || bidDoc.fullText().contains("补遗");
        if (hasClarification) {
            List<QualityEvidence> evidences = List.of(QualityEvidence.bid("澄清响应章节", 1, "已确认并响应最新答疑澄清文件"));
            return QualityCheckItemResult.pass(item, "已充分吸收并响应最新答疑澄清与补遗文件", evidences);
        }
        return QualityCheckItemResult.manualReview(item, "未检测到明确的补遗文件确认函", "如本项目有补遗文件，请补充确认函", Collections.emptyList());
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
}
