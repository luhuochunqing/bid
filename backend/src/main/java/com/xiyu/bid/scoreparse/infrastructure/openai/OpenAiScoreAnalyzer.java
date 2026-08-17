// Input: 招标文件全文/Markdown + structuredMetadata + 进度回调
// Output: List<ScoreCandidate>（四路召回合并前的候选池）/ 回补候选
// Pos: scoreparse/infrastructure/openai — LLM 编排（召回三/四 + 完整性回补）
// 维护声明: 维护者按项目SOP；spec 041 FR-001 四路召回、FR-005 二次回补
package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.biddraftagent.infrastructure.openai.ScoringItemExtractor;
import com.xiyu.bid.biddraftagent.infrastructure.openai.TenderIntakeTextProcessor;
import com.xiyu.bid.docinsight.domain.DocumentChunk;
import com.xiyu.bid.docinsight.domain.StructuralDocumentChunker;
import com.xiyu.bid.scoreparse.application.ScoreDocExcerptExtractor;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.infrastructure.structure.MarkdownScoreSectionLocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 评分标准解析 LLM 编排器（spec 041 FR-001 四路召回）。
 *
 * <p>召回一（正则规则）：复用 {@link ScoringItemExtractor}，输入为召回二定位的候选区域。
 * <p>召回二（文档结构）：复用 {@link MarkdownScoreSectionLocator} 定位评分表候选区域。
 * <p>召回三/四（评分规则语义 + LLM 全文语义）：{@link StructuralDocumentChunker} 切片，
 * 每 chunk 一次 LLM 结构化调用（复用 {@link OpenAiStructuredOutputService}）。
 *
 * <p>合并去重由 AppService 编排 {@code ScoreItemMergePolicy} 完成，本类只负责召回。
 * 所有文档内容过 {@link TenderIntakeTextProcessor#sanitizeUntrusted}（安全契约）。
 * 单 chunk LLM 失败不阻断整体（log.warn 继续）；正则召回始终可用作兜底。
 */
@Service
@Profile("!e2e")
@Slf4j
public class OpenAiScoreAnalyzer {

    private static final String USE_CASE = "score parse analysis";
    private static final int SOURCE_TEXT_MAX_CHARS = 500;

    private final OpenAiStructuredOutputService structuredOutputService;
    private final OpenAiBidAgentConfigurationResolver configurationResolver;
    private final StructuralDocumentChunker structuralChunker;

    public OpenAiScoreAnalyzer(
            OpenAiStructuredOutputService pStructuredOutputService,
            OpenAiBidAgentConfigurationResolver pConfigurationResolver,
            StructuralDocumentChunker pStructuralChunker
    ) {
        this.structuredOutputService = pStructuredOutputService;
        this.configurationResolver = pConfigurationResolver;
        this.structuralChunker = pStructuralChunker;
    }

    /** 进度回调：(progress 0-100, stage 描述) */
    public interface ProgressReporter extends BiConsumer<Integer, String> {
    }

    /**
     * 四路召回，返回合并前的候选池。
     *
     * @param fullText          招标文件全文
     * @param structuredMetadata doc-insight 结构化元数据（可为 null）
     * @param progress          进度回调（可为 null）
     */
    public List<ScoreCandidate> recallCandidates(
            String fullText, String structuredMetadata, ProgressReporter progress) {
        report(progress, 5, "召回一/二：关键词正则规则 + 文档结构定位");
        List<ScoreCandidate> candidates = new ArrayList<>();
        candidates.addAll(recallLane1RegexRules(fullText));
        candidates.addAll(recallLane2DocumentStructure(fullText));

        report(progress, 15, "召回三/四：评分规则语义切片 + LLM 全文语义提取");
        candidates.addAll(recallLane3ScoreSemanticPatterns(fullText));
        candidates.addAll(recallLane4LlmFullText(fullText, structuredMetadata, progress));
        return candidates;
    }

    /** 召回一：关键词与正则规则提取 */
    private List<ScoreCandidate> recallLane1RegexRules(String fullText) {
        List<ScoreCandidate> list = new ArrayList<>();
        for (ScoringCriterion criterion : ScoringItemExtractor.extract(fullText)) {
            list.add(toCandidate(criterion, null));
        }
        log.info("召回一（正则规则）完成: count={}", list.size());
        return list;
    }

    /** 召回二：文档章节与表格结构定位提取 */
    private List<ScoreCandidate> recallLane2DocumentStructure(String fullText) {
        List<MarkdownScoreSectionLocator.ScoreSection> sections = MarkdownScoreSectionLocator.locate(fullText);
        List<ScoreCandidate> list = new ArrayList<>();
        for (MarkdownScoreSectionLocator.ScoreSection section : sections) {
            for (ScoringCriterion criterion : ScoringItemExtractor.extract(section.content())) {
                list.add(toCandidate(criterion, section));
            }
        }
        log.info("召回二（文档结构）完成: sections={}, count={}", sections.size(), list.size());
        return list;
    }

    /** 召回三：评分规则专用语义模式（评审办法、评分细则关键区域）提取 */
    private List<ScoreCandidate> recallLane3ScoreSemanticPatterns(String fullText) {
        List<ScoreCandidate> list = new ArrayList<>();
        List<String> semanticParagraphs = ScoreDocExcerptExtractor.extractSemanticScoreParagraphs(fullText);
        for (String paragraph : semanticParagraphs) {
            for (ScoringCriterion criterion : ScoringItemExtractor.extract(paragraph)) {
                list.add(toCandidate(criterion, null));
            }
        }
        log.info("召回三（评分规则语义）完成: count={}", list.size());
        return list;
    }

    /** 召回四：chunk 切片多轮 LLM 全文结构化提取；单 chunk 失败不阻断 */
    private List<ScoreCandidate> recallLane4LlmFullText(
            String fullText, String structuredMetadata, ProgressReporter progress) {
        List<DocumentChunk> chunks = structuralChunker.chunk(fullText, structuredMetadata);
        List<ScoreCandidate> candidates = new ArrayList<>();
        if (chunks.isEmpty()) {
            log.warn("结构化切片为空，跳过 LLM 召回");
            return candidates;
        }
        OpenAiBidAgentRequestConfig config = configurationResolver.resolve(USE_CASE);
        int llmProgressSpan = 60; // 15% → 75%
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            String prompt = ScoreParsePrompts.buildCandidateExtractionPrompt(
                    TenderIntakeTextProcessor.sanitizeUntrusted(chunk.text()),
                    i + 1, chunks.size());
            try {
                ScoreCandidateOutput output = structuredOutputService.request(
                        prompt, ScoreCandidateOutput.class, config,
                        "AI structured response did not include score candidates");
                if (output != null && output.candidates != null) {
                    output.candidates.stream()
                            .filter(candidate -> candidate != null && candidate.detail != null)
                            .map(this::toCandidate)
                            .forEach(candidates::add);
                }
            } catch (RuntimeException exception) {
                log.warn("评分候选 LLM 提取失败 chunk {}/{}: {} — 跳过该 chunk",
                        i + 1, chunks.size(), exception.getMessage());
            }
            report(progress, 15 + llmProgressSpan * (i + 1) / chunks.size(),
                    "召回三/四：LLM 语义提取 " + (i + 1) + "/" + chunks.size());
        }
        log.info("召回四（LLM 全文语义）完成: chunks={}, count={}", chunks.size(), candidates.size());
        return candidates;
    }

    /**
     * 完整性回补扫描（FR-005：WeightSumCheck 合计≠100 或存在未覆盖区域时触发）。
     * LLM 失败返回空列表（不阻断主流程，仅记录警告）。
     */
    public List<ScoreCandidate> recheckGaps(
            String fullTextExcerpt, List<ScoreCandidate> knownItems) {
        OpenAiBidAgentRequestConfig config = configurationResolver.resolve(USE_CASE);
        String prompt = ScoreParsePrompts.buildGapRecheckPrompt(
                TenderIntakeTextProcessor.sanitizeUntrusted(fullTextExcerpt),
                summarizeKnownItems(knownItems),
                "footnotes, table-remarks, cross-page-refs");
        try {
            ScoreGapRecheckOutput output = structuredOutputService.request(
                    prompt, ScoreGapRecheckOutput.class, config,
                    "AI structured response did not include gap recheck result");
            if (output == null || output.missedItems == null) {
                return List.of();
            }
            return output.missedItems.stream()
                    .filter(candidate -> candidate != null && candidate.detail != null)
                    .map(this::toCandidate)
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("完整性回补扫描失败（忽略，不阻断主流程）: {}", exception.getMessage());
            return List.of();
        }
    }

    /**
     * 阶段 2：客观项对标打分（每评分项一次 LLM 调用，契约 §4）。
     * <p>输出数值不经守卫直接返回——由调用方（ScoreScoringAppService）过
     * {@code ScoreAssessmentGuard}（超区间置空 / quoteMissing 置空）。
     * 单项调用失败抛 RuntimeException（由编排层决定任务级失败语义）。
     *
     * @param scoreItemDetail 评分项详细要素（完整原文）
     * @param weight          权重满分
     * @param bidDocExcerpt   投标文件节选（已由调用方截取）
     */
    public ScoreAssessmentOutput assessObjective(
            String scoreItemDetail, java.math.BigDecimal weight, String bidDocExcerpt) {
        OpenAiBidAgentRequestConfig config = configurationResolver.resolve(USE_CASE);
        String prompt = ScoreParsePrompts.buildObjectiveAssessmentPrompt(
                TenderIntakeTextProcessor.sanitizeUntrusted(scoreItemDetail),
                weight.doubleValue(),
                TenderIntakeTextProcessor.sanitizeUntrusted(bidDocExcerpt));
        return structuredOutputService.request(
                prompt, ScoreAssessmentOutput.class, config,
                "AI structured response did not include score assessment");
    }

    /**
     * 阶段 2：主观项建议（仅 suggestion，禁止数字得分，SC-003 零泄漏）。
     * 模型违规输出数字时由 {@code ScoreAssessmentGuard} 强制丢弃。
     */
    public ScoreAssessmentOutput assessSubjective(String scoreItemDetail, String bidDocExcerpt) {
        OpenAiBidAgentRequestConfig config = configurationResolver.resolve(USE_CASE);
        String prompt = ScoreParsePrompts.buildSubjectiveSuggestionPrompt(
                TenderIntakeTextProcessor.sanitizeUntrusted(scoreItemDetail),
                TenderIntakeTextProcessor.sanitizeUntrusted(bidDocExcerpt));
        return structuredOutputService.request(
                prompt, ScoreAssessmentOutput.class, config,
                "AI structured response did not include subjective suggestion");
    }

    private String summarizeKnownItems(List<ScoreCandidate> knownItems) {
        if (knownItems == null || knownItems.isEmpty()) {
            return "（无已提取项）";
        }
        StringBuilder sb = new StringBuilder();
        for (ScoreCandidate item : knownItems) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(safeText(item.code())).append(' ')
                    .append(safeText(item.dim())).append(' ')
                    .append(item.weight() == null ? "?" : item.weight()).append("分");
        }
        return sb.toString();
    }

    private ScoreCandidate toCandidate(ScoringCriterion criterion,
                                       MarkdownScoreSectionLocator.ScoreSection section) {
        String detail = criterion.indicator() != null && !criterion.indicator().isBlank()
                ? criterion.indicator() : criterion.dimension();
        // 召回一/三无章节定位时传 null，此时章节相关字段降级为空串（2026-08-17 项目 226 NPE 回归）
        boolean hasSection = section != null;
        return new ScoreCandidate(
                safeText(criterion.itemNumber()),
                safeText(criterion.dimension()),
                detail,
                criterion.weight(),
                null,
                hasSection ? section.sectionTitle() : "",
                hasSection ? truncate(section.content()) : "",
                hasSection ? section.location() : "",
                "RECALL_REGEX_STRUCTURE"
        );
    }

    private ScoreCandidate toCandidate(ScoreCandidateOutput.Candidate candidate) {
        return new ScoreCandidate(
                safeText(candidate.code),
                safeText(candidate.dim),
                candidate.detail,
                candidate.weight == null ? null : java.math.BigDecimal.valueOf(candidate.weight),
                safeText(candidate.scoreTypeGuess),
                safeText(candidate.contextNote),
                truncate(safeText(candidate.sourceText)),
                safeText(candidate.location),
                safeText(candidate.semanticPattern)
        );
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= SOURCE_TEXT_MAX_CHARS ? text : text.substring(0, SOURCE_TEXT_MAX_CHARS);
    }

    private static void report(ProgressReporter progress, int value, String stage) {
        if (progress != null) {
            progress.accept(value, stage);
        }
    }
}
