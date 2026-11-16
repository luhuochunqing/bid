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
        return SemanticEvidenceBoundary.reconcile(adjudicateCandidate(item, bidDoc, tenderDoc, requirements, kbMatches, kbEvidences),
                bidDoc);
    }

    /** 引擎候选入口：最终证据护栏由引擎统一执行；公开直接调用仍保留护栏。 */
    QualityCheckItemResult adjudicateCandidate(BidQualityCheckItem item, DocumentContent bidDoc, DocumentContent tenderDoc,
            TenderRequirements requirements, List<KnowledgeMatch> kbMatches, List<QualityEvidence> kbEvidences) {
        List<QualityEvidence> evidences =
                EvidenceAssembler.semanticEvidences(item, bidDoc, tenderDoc, requirements, kbEvidences);
        LlmVerdict.ChatOutcome<LlmVerdict> outcome =
                port.chat(buildPrompt(item, bidDoc, tenderDoc, requirements, kbMatches));
        if (outcome instanceof LlmVerdict.Success<LlmVerdict> success) {
            return toResult(item, success.verdict(), bindModelEvidence(success.verdict(), bidDoc, evidences));
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

    /** 模型没有给出可验证的触发摘录时，不用通用关键词首命中冒充问题位置。 */
    private static List<QualityEvidence> bindModelEvidence(LlmVerdict verdict, DocumentContent bid,
            List<QualityEvidence> fallback) {
        var result = new java.util.ArrayList<>(fallback.stream().filter(e -> !"BID".equals(e.sourceType())).toList());
        if (verdict == null || bid == null || verdict.evidenceChunk() == null
                || verdict.evidenceQuote() == null || verdict.evidenceQuote().isBlank()) return result;
        var hits = bid.pages().stream().filter(p -> p.pageNumber() == verdict.evidenceChunk())
                .filter(p -> p.text().contains(verdict.evidenceQuote()))
                .filter(p -> !BidLetterCheckPolicy.isCatalogOrOutlinePage(p.text())).toList();
        if (hits.size() == 1) {
            result.add(QualityEvidence.bid("核查触发原文", hits.getFirst().pageNumber(), verdict.evidenceQuote()));
        }
        return result;
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
     * 组装裁决 prompt：招标结构化要求 + 知识库真实记录 + 文档按语义检索相关页（固定 JSON 输出契约）.
     */
    public static String buildPrompt(
            final BidQualityCheckItem item,
            final DocumentContent bidDoc,
            final DocumentContent tenderDoc,
            final TenderRequirements requirements,
            final List<KnowledgeMatch> kbMatches) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是投标文件质量核查专家。请仅依据下列材料裁决检查项「").append(item.getTitle())
                .append("」，输出且仅输出单个合法闭合的 JSON 对象：{\"status\":\"PASS|FAIL|MANUAL\",\"conclusion\":\"...\","
                        + "\"suggestion\":\"...\",\"reasonCode\":\"\",\"evidenceChunk\":null,\"evidenceQuote\":\"\"}（conclusion 请精炼在 200 字内禁止冗长输出；status=MANUAL 时 reasonCode 必填，原因码为LOW_CONFIDENCE、AMBIGUOUS、EVIDENCE_INSUFFICIENT、REFERENCE_MISSING或MANUAL_REVIEW_REQUIRED）。\n");
        sb.append("定位须引用投标文件中的逐字原文到evidenceQuote，并填写其内容块ID到evidenceChunk；无法定位时分别为空字符串和null。"
                + "仅标明物理页且未额外标内容块ID的片段，其内容块ID等于该页编号。内容块不是原件页码，不得在结论或建议中将内容块序号称为页码。\n");
        sb.append(AdjudicationGuidelines.allGuidelines());
        sb.append("【招标要求（LLM 结构化提取）】\n").append(summarizeRequirements(requirements)).append("\n\n");
        sb.append("【企业知识库真实记录】\n").append(summarizeKbMatches(kbMatches)).append("\n\n");
        sb.append("【招标文件相关内容】\n")
                .append(SemanticPageRetriever.retrieveContext(
                        item, requirements, tenderDoc, SemanticPageRetriever.DEFAULT_TENDER_BUDGET))
                .append("\n\n");
        sb.append("【投标文件相关内容】\n")
                .append(SemanticPageRetriever.retrieveContext(
                        item, requirements, bidDoc, SemanticPageRetriever.DEFAULT_BID_BUDGET))
                .append("\n");
        return sb.toString();
    }

    private static String summarizeRequirements(final TenderRequirements requirements) {
        if (requirements.missing()) {
            return "（招标要求不可用）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("资格条件结构化依据（UNKNOWN必须人工复核，产品/人员证书不得当作企业资质）: ")
                .append(requirements.qualificationConditions()).append('\n');
        sb.append("评分项: ").append(requirements.scoringItems().stream()
                .map(TenderRequirements.ScoringItem::name).collect(Collectors.joining("；"))).append('\n');
        sb.append("否决条款: ").append(requirements.vetoClauses().stream()
                .map(TenderRequirements.VetoClause::clause).collect(Collectors.joining("；"))).append('\n');
        if (requirements.depositRequirement() != null) {
            String desc = requirements.depositRequirement().description();
            sb.append("保证金要求: ").append(desc != null && !desc.isBlank()
                    ? desc
                    : (Boolean.FALSE.equals(requirements.depositRequirement().required()) ? "免收/不涉及" : "需缴纳")).append('\n');
        }
        if (requirements.bidLetterRequirement() != null) {
            String desc = requirements.bidLetterRequirement().description();
            sb.append("投标函要求: ").append(desc != null && !desc.isBlank()
                    ? desc
                    : "类型: " + requirements.bidLetterRequirement().letterType()
                            + ", 抬头: " + requirements.bidLetterRequirement().recipientName()
                            + ", 有效期: " + requirements.bidLetterRequirement().validityDays() + "天").append('\n');
        }
        if (requirements.authorizationRequirement() != null) {
            String desc = requirements.authorizationRequirement().description();
            sb.append("授权书要求: ").append(desc != null && !desc.isBlank()
                    ? desc
                    : (Boolean.TRUE.equals(requirements.authorizationRequirement().allowLegalRepresentativeOnly())
                            ? "需提供法定代表人证明书；法定代表人亲自参加免授权书"
                            : "需提供法定代表人证明书及授权委托书")).append('\n');
        }
        if (requirements.businessLicenseRequirement() != null) {
            String desc = requirements.businessLicenseRequirement().description();
            sb.append("营业执照要求: ").append(desc != null && !desc.isBlank()
                    ? desc
                    : "须提供有效的营业执照或主体资格证明文件（复印件加盖公章）").append('\n');
        }
        if (requirements.idAuthLetterRequirement() != null) {
            String desc = requirements.idAuthLetterRequirement().description();
            sb.append("身份证件要求: ").append(desc != null && !desc.isBlank()
                    ? desc
                    : "须提供法定代表人及委托代理人身份证复印件或扫描件"
                            + (requirements.idAuthLetterRequirement().requireSocialSecurity() ? "，并提供社保证明" : "")).append('\n');
        }
        if (requirements.financialRequirement() != null) {
            String desc = requirements.financialRequirement().description();
            FinancialRequirement financial = requirements.financialRequirement();
            sb.append("财务状况要求: ").append(Boolean.FALSE.equals(financial.required())
                    ? "招标文件明确不要求财务证明"
                    : (desc != null && !desc.isBlank() ? desc
                            : "是否必需=" + financial.required() + "；审计报告=" + financial.auditReportRequired()
                                    + "；报表=" + String.join("、", financial.requiredStatements())
                                    + "；会计师事务所印章=" + financial.requireCpaSeal())).append('\n');
        }
        if (requirements.priceRequirement() != null) {
            String desc = requirements.priceRequirement().description();
            sb.append("报价要求: ").append(desc != null && !desc.isBlank()
                    ? desc
                    : (requirements.priceRequirement().maxPrice() != null
                            ? "最高投标限价: " + requirements.priceRequirement().maxPrice() + "元"
                            : (requirements.priceRequirement().maxPriceRawText() != null
                                    ? "最高限价: " + requirements.priceRequirement().maxPriceRawText()
                                    : "按规定填报报价"))).append('\n');
        }
        if (requirements.caCertificateRequirement() != null) {
            String desc = requirements.caCertificateRequirement().description();
            sb.append("CA证书要求: ").append(desc != null && !desc.isBlank()
                    ? desc
                    : (Boolean.FALSE.equals(requirements.caCertificateRequirement().required())
                            ? "免收/不涉及CA证书"
                            : "电子交易平台: " + (requirements.caCertificateRequirement().platformName() != null ? requirements.caCertificateRequirement().platformName() : "指定平台") + "，须完成CA数字证书办理及有效性验证")).append('\n');
        }
        if (requirements.frontTableRequirement() != null) {
            String desc = requirements.frontTableRequirement().description();
            sb.append("前附表要求: ").append(desc != null && !desc.isBlank()
                    ? desc
                    : "须对照前附表要求完整响应工期、质保期、投标有效期、交货地点、付款方式等关键条款").append('\n');
        }
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
