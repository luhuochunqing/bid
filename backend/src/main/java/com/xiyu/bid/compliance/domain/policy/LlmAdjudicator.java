// Input: 语义检查项 + 投标/招标文件 + TenderRequirements + KnowledgeMatch + LLM 通道（port，值返回）
// Output: 单项裁决 PASS/FAIL/MANUAL（reasonCode ∈ PRD §3.5）+ 真实定位证据；失败/Noop 降级 MANUAL 不阻断，兜底结论为用户可读文案（不暴露技术细节）
// Pos: compliance/domain/policy/ - LLM 混合裁决 lane（spec 049 US5 / T047-T049，research.md R6，FP-Java：值返回无异常流）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * LLM 混合裁决 lane（纯核心，spec 049 第五步）.
 *
 * <p>语义项经 {@link LlmVerdict.LlmChatPort} 交由真实 LLM 裁决，LLM 只做裁决层不做全量直出；
 * 通道侧一切失败（AI 未启用 / 调用异常 / 响应非法）由边界适配器吸收为
 * {@link LlmVerdict.ChatOutcome} 值，本 lane 无异常流：
 * 单项失败置 MANUAL + LOW_CONFIDENCE 不阻断整体，兜底结论为用户可读文案（不暴露技术细节）；
 * AI 未启用（Noop）时语义项全部 MANUAL 并注明原因，任务保持 COMPLETED（PRD §3.5 / research.md R6）.
 */
public final class LlmAdjudicator {

    /** 进度回调（参照 scoreparse OpenAiScoreAnalyzer 范式）：progress 0-100 + 当前步骤文案. */
    @FunctionalInterface
    public interface ProgressReporter extends BiConsumer<Integer, String> {
    }

    /** prompt 内投标/招标原文摘录预算. */
    private static final int BID_EXCERPT_BUDGET = 6000;
    private static final int TENDER_EXCERPT_BUDGET = 4000;
    /** prompt 内知识库记录条数上限. */
    private static final int MAX_KB_MATCHES_IN_PROMPT = 20;
    /** 单条知识库记录摘要长度上限. */
    private static final int KB_MATCH_TEXT_BUDGET = 120;

    /** 裁决失败兜底结论（用户可读，不暴露技术细节）. */
    private static final String FALLBACK_INVALID_CONCLUSION =
            "AI 本次未能自动裁决该条款（返回结果异常），已转人工确认，请对照检查内容人工复核";
    private static final String FALLBACK_FAILURE_CONCLUSION =
            "AI 本次未能自动裁决该条款（调用异常），已转人工确认，请对照检查内容人工复核";
    private static final String FALLBACK_MANUAL_SUGGESTION = "请人工复核该检查项内容后确认";

    private final LlmVerdict.LlmChatPort<LlmVerdict> port;

    public LlmAdjudicator(final LlmVerdict.LlmChatPort<LlmVerdict> port) {
        this.port = port;
    }

    /**
     * 对全部 7 项语义条款执行 LLM 裁决（含进度上报）.
     *
     * @param failedKnowledgeSources 失败知识库源（供 prompt 注明数据缺口；guard 由规则 lane 负责）
     * @param progress 进度回调，每完成一项上报一次，最后一项 progress=100
     * @return itemCode → 裁决结果（覆盖全部语义项）
     */
    public Map<String, QualityCheckItemResult> adjudicate(
            final DocumentContent bidDoc,
            final DocumentContent tenderDoc,
            final TenderRequirements requirements,
            final List<KnowledgeMatch> kbMatches,
            final Set<String> failedKnowledgeSources,
            final ProgressReporter progress) {
        List<QualityEvidence> kbEvidences = kbMatches == null ? List.of()
                : kbMatches.stream().map(QualityEvidence::fromKnowledgeMatch).toList();
        Map<String, QualityCheckItemResult> outcomes = new LinkedHashMap<>();
        List<BidQualityCheckItem> items = SemanticRulesLane.SEMANTIC_ITEMS;
        for (int i = 0; i < items.size(); i++) {
            BidQualityCheckItem item = items.get(i);
            outcomes.put(item.getCode(),
                    adjudicateItem(item, bidDoc, tenderDoc, requirements, kbMatches, kbEvidences));
            progress.accept((i + 1) * 100 / items.size(), item.getTitle());
        }
        return outcomes;
    }

    /**
     * 单项 LLM 语义裁决：任何失败分支都降级 MANUAL，不抛异常、不阻断整体.
     *
     * <p>用 instanceof 序贯判定而非 switch 模式匹配：后者编译产物含 MatchException 构造，
     * 会触发 FP 门禁（纯核心禁止构造 Throwable）.
     */
    public QualityCheckItemResult adjudicateItem(
            final BidQualityCheckItem item,
            final DocumentContent bidDoc,
            final DocumentContent tenderDoc,
            final TenderRequirements requirements,
            final List<KnowledgeMatch> kbMatches,
            final List<QualityEvidence> kbEvidences) {
        List<QualityEvidence> evidences =
                EvidenceAssembler.semanticEvidences(item, bidDoc, tenderDoc, requirements, kbEvidences);
        LlmVerdict.ChatOutcome<LlmVerdict> outcome =
                port.chat(buildPrompt(item, bidDoc, tenderDoc, requirements, kbMatches));
        if (outcome instanceof LlmVerdict.Success<LlmVerdict> success) {
            return toResult(item, success.verdict(), evidences);
        }
        if (outcome instanceof LlmVerdict.Noop<LlmVerdict>) {
            return ManualReasons.manual(item,
                    ManualReasons.MANUAL_REVIEW_REQUIRED, "AI 未启用，语义条款未经 LLM 裁决，请人工确认",
                    "请在系统设置中启用 AI 后重新核查", evidences);
        }
        if (outcome instanceof LlmVerdict.Failure<LlmVerdict>) {
            return ManualReasons.manual(item,
                    ManualReasons.LOW_CONFIDENCE, FALLBACK_FAILURE_CONCLUSION,
                    FALLBACK_MANUAL_SUGGESTION, evidences);
        }
        return ManualReasons.manual(item,
                ManualReasons.LOW_CONFIDENCE, FALLBACK_INVALID_CONCLUSION,
                FALLBACK_MANUAL_SUGGESTION, evidences);
    }

    /**
     * 裁决结论 → 核查结果；结论未通过结构校验时降级 LOW_CONFIDENCE.
     */
    private QualityCheckItemResult toResult(
            final BidQualityCheckItem item, final LlmVerdict verdict, final List<QualityEvidence> evidences) {
        if (verdict == null || verdict.validated().isEmpty()) {
            return ManualReasons.manual(item, ManualReasons.LOW_CONFIDENCE,
                    FALLBACK_INVALID_CONCLUSION, FALLBACK_MANUAL_SUGGESTION, evidences);
        }
        return switch (verdict.status()) {
            case "PASS" -> QualityCheckItemResult.pass(item, verdict.conclusion(), evidences);
            case "FAIL" -> QualityCheckItemResult.fail(item, verdict.conclusion(),
                    verdict.suggestion().isBlank() ? "请按招标要求整改对应条款" : verdict.suggestion(), evidences);
            default -> ManualReasons.manual(item,
                    verdict.reasonCode().isBlank() ? ManualReasons.AMBIGUOUS : verdict.reasonCode(),
                    verdict.conclusion(),
                    verdict.suggestion().isBlank() ? "请人工复核该语义条款" : verdict.suggestion(), evidences);
        };
    }

    /**
     * 组装裁决 prompt：招标结构化要求 + 知识库真实记录 + 文档原文摘录（固定 JSON 输出契约）.
     */
    static String buildPrompt(
            final BidQualityCheckItem item,
            final DocumentContent bidDoc,
            final DocumentContent tenderDoc,
            final TenderRequirements requirements,
            final List<KnowledgeMatch> kbMatches) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是投标文件质量核查专家。请仅依据下列材料裁决检查项「").append(item.getTitle())
                .append("」，输出且仅输出 JSON 对象：{\"status\":\"PASS|FAIL|MANUAL\",\"conclusion\":\"...\","
                        + "\"suggestion\":\"...\",\"reasonCode\":\"EVIDENCE_INSUFFICIENT|AMBIGUOUS|LOW_CONFIDENCE"
                        + "|REFERENCE_MISSING|MANUAL_REVIEW_REQUIRED\"}（status=MANUAL 时 reasonCode 必填）。\n\n");
        sb.append("【招标要求（LLM 结构化提取）】\n").append(summarizeRequirements(requirements)).append("\n\n");
        sb.append("【企业知识库真实记录】\n").append(summarizeKbMatches(kbMatches)).append("\n\n");
        sb.append("【招标文件摘录】\n").append(excerpt(tenderDoc.fullText(), TENDER_EXCERPT_BUDGET)).append("\n\n");
        sb.append("【投标文件摘录】\n").append(excerpt(bidDoc.fullText(), BID_EXCERPT_BUDGET)).append("\n");
        return sb.toString();
    }

    private static String excerpt(final String text, final int budget) {
        if (text == null || text.isBlank()) {
            return "（无内容）";
        }
        return text.length() <= budget ? text : text.substring(0, budget) + "……（已截断）";
    }

    private static String summarizeRequirements(final TenderRequirements requirements) {
        if (requirements.missing()) {
            return "（招标要求不可用）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("硬性资格要求: ").append(String.join("；", requirements.mandatoryQualifications())).append('\n');
        sb.append("评分项: ").append(requirements.scoringItems().stream()
                .map(TenderRequirements.ScoringItem::name).collect(Collectors.joining("；"))).append('\n');
        sb.append("否决条款: ").append(requirements.vetoClauses().stream()
                .map(TenderRequirements.VetoClause::clause).collect(Collectors.joining("；")));
        return sb.toString();
    }

    private static String summarizeKbMatches(final List<KnowledgeMatch> kbMatches) {
        if (kbMatches == null || kbMatches.isEmpty()) {
            return "（知识库无匹配记录）";
        }
        return kbMatches.stream()
                .limit(MAX_KB_MATCHES_IN_PROMPT)
                .map(m -> "[" + m.knowledgeSource() + (m.matched() ? "/已匹配" : "/未匹配") + "] "
                        + truncate(m.content(), KB_MATCH_TEXT_BUDGET))
                .collect(Collectors.joining("\n"));
    }

    private static String truncate(final String text, final int budget) {
        if (text == null) {
            return "";
        }
        return text.length() <= budget ? text : text.substring(0, budget) + "…";
    }
}
