// Input: 招标文件全文/Markdown + structuredMetadata + 进度回调
// Output: List<ScoreCandidate>（四路召回合并前的候选池）/ 回补候选
// Pos: scoreparse/infrastructure/openai — LLM 编排（召回三/四 + 完整性回补）
// 维护声明: 维护者按项目SOP；spec 041 FR-001 四路召回、FR-005 二次回补；候选映射拆至 ScoreCandidateMapper（2026-08-19 满足 300 行预算）
package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.biddraftagent.domain.PriceScoringKeywords;
import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.biddraftagent.infrastructure.openai.MultimodalImage;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.biddraftagent.infrastructure.openai.ScoringItemExtractor;
import com.xiyu.bid.biddraftagent.infrastructure.openai.TenderIntakeTextProcessor;
import com.xiyu.bid.docinsight.domain.DocumentChunk;
import com.xiyu.bid.docinsight.domain.StructuralDocumentChunker;
import com.xiyu.bid.scoreparse.application.ScoreDocExcerptExtractor;
import com.xiyu.bid.scoreparse.domain.DocumentImage;
import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.infrastructure.structure.MarkdownScoreSectionLocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

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
 * 单 chunk LLM 失败重试 1 次后跳过不阻断（skippedChunks 计数，spec 046 US3）；
 * 正则召回始终可用作兜底。
 */
@Service
@Profile("!e2e")
@Slf4j
public class OpenAiScoreAnalyzer {

    private static final String USE_CASE = "score parse analysis";

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
    public interface ProgressReporter extends BiConsumer<Integer, String> {}

    /**
     * 四路召回，返回合并前的候选池与跳过 chunk 数（spec 046 US3 可见性）。
     */
    public RecallOutcome recallCandidates(
            String fullText, String structuredMetadata, ProgressReporter progress) {
        report(progress, 5, "召回一/二：关键词正则规则 + 文档结构定位");
        List<ScoreCandidate> lane1 = recallLane1RegexRules(fullText);
        List<ScoreCandidate> lane2 = recallLane2DocumentStructure(fullText);

        report(progress, 15, "召回三/四：评分规则语义切片 + LLM 全文语义提取");
        List<ScoreCandidate> lane3 = recallLane3ScoreSemanticPatterns(fullText);
        RecallOutcome lane4 = recallLane4LlmFullText(fullText, structuredMetadata, progress);
        List<ScoreCandidate> candidates = new ArrayList<>();
        candidates.addAll(lane1);
        candidates.addAll(lane2);
        candidates.addAll(lane3);
        candidates.addAll(lane4.candidates());
        // spec 057 FR-05：lane 贡献度 + 对齐统计 + 模型名随 outcome 上报（run-summary 数据源）
        Map<String, Integer> lanes = new LinkedHashMap<>();
        lanes.put("lane1", lane1.size());
        lanes.put("lane2", lane2.size());
        lanes.put("lane3", lane3.size());
        lanes.put("lane4", lane4.candidates().size());
        return new RecallOutcome(candidates, lane4.skippedChunks(), lanes,
                lane4.alignedHit(), lane4.candidates().size(), lane4.llmModel());
    }

    /** 召回一：关键词与正则规则提取 */
    private List<ScoreCandidate> recallLane1RegexRules(String fullText) {
        List<ScoreCandidate> list = new ArrayList<>();
        for (ScoringCriterion criterion : ScoringItemExtractor.extract(fullText)) {
            list.add(ScoreCandidateMapper.fromCriterion(criterion, null));
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
                list.add(ScoreCandidateMapper.fromCriterion(criterion, section));
            }
        }
        log.info("召回二（文档结构）完成: sections={}, count={}", sections.size(), list.size());
        return list;
    }

    /** 召回三：评分规则专用语义模式（评审办法、评分细则关键区域）提取 */
    private List<ScoreCandidate> recallLane3ScoreSemanticPatterns(String fullText) {
        List<ScoreCandidate> list = new ArrayList<>();
        for (String paragraph : ScoreDocExcerptExtractor.extractSemanticScoreParagraphs(fullText)) {
            for (ScoringCriterion criterion : ScoringItemExtractor.extract(paragraph)) {
                list.add(ScoreCandidateMapper.fromCriterion(criterion, null));
            }
        }
        log.info("召回三（评分规则语义）完成: count={}", list.size());
        return list;
    }

    private static final Pattern SCORE_CHUNK_KEYWORD_PATTERN = Pattern.compile(
            "评分|评标|评审|分值|满分|得分|评分项|详细评审|商务标|技术标|综合评估|价格分|商务分|技术分|加分|扣分|分细则"
            + "|" + PriceScoringKeywords.SCORE_EXTENSION);
    private static final Pattern PRICE_PARAM_PATTERN = PriceScoringKeywords.PRICE_PARAM_BLOCK;

    /** 召回四：chunk 切片多轮 LLM 全文结构化提取；单 chunk 两连败跳过不阻断 */
    private RecallOutcome recallLane4LlmFullText(
            String fullText, String structuredMetadata, ProgressReporter progress) {
        List<DocumentChunk> allChunks = structuralChunker.chunk(fullText, structuredMetadata);
        List<DocumentChunk> chunks = filterRelevantChunks(allChunks);
        List<ScoreCandidate> candidates = new ArrayList<>();
        if (chunks.isEmpty()) {
            log.warn("结构化切片为空，跳过 LLM 召回");
            return new RecallOutcome(candidates, 0);
        }
        OpenAiBidAgentRequestConfig config = configurationResolver.resolve(USE_CASE);
        int llmProgressSpan = 60; // 15% → 75%
        int skipped = 0;
        int alignedCount = 0;
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            String chunkText = TenderIntakeTextProcessor.sanitizeUntrusted(chunk.text());
            String prompt = ScoreParsePrompts.buildCandidateExtractionPrompt(
                    chunkText, i + 1, chunks.size());
            ScoreCandidateOutput output = requestCandidatesWithRetry(prompt, config);
            if (output == null) {
                skipped++;
                log.warn("评分候选 LLM 提取两连败 chunk {}/{} — 跳过该 chunk", i + 1, chunks.size());
            } else if (output.candidates != null) {
                List<ScoreCandidate> chunkCandidates = output.candidates.stream()
                        .filter(candidate -> candidate != null && candidate.detail != null)
                        .map(candidate -> ScoreCandidateMapper.fromLlmCandidate(candidate, chunkText))
                        .toList();
                alignedCount += ScoreCandidateMapper.countAligned(chunkCandidates, chunkText);
                candidates.addAll(chunkCandidates);
            }
            report(progress, 15 + llmProgressSpan * (i + 1) / chunks.size(),
                    "召回三/四：LLM 语义提取 " + (i + 1) + "/" + chunks.size());
        }
        log.info("召回四（LLM 全文语义）完成: chunks={}/{}, count={}, skipped={}, aligned={}/{}",
                chunks.size(), allChunks.size(), candidates.size(), skipped, alignedCount, candidates.size());
        return new RecallOutcome(candidates, skipped,
                Map.of("lane4", candidates.size()), alignedCount, candidates.size(), config.model());
    }

    private List<DocumentChunk> filterRelevantChunks(List<DocumentChunk> allChunks) {
        if (allChunks.size() <= 4) return allChunks;
        boolean[] isRelevant = new boolean[allChunks.size()];
        int initialRelevant = 0;
        for (int idx = 0; idx < allChunks.size(); idx++) {
            DocumentChunk chunk = allChunks.get(idx);
            boolean pathMatches = chunk.sectionPath() != null && chunk.sectionPath().stream()
                    .anyMatch(p -> p != null && SCORE_CHUNK_KEYWORD_PATTERN.matcher(p).find());
            isRelevant[idx] = pathMatches || SCORE_CHUNK_KEYWORD_PATTERN.matcher(chunk.text()).find()
                    || PRICE_PARAM_PATTERN.matcher(chunk.text()).find();
            if (isRelevant[idx]) initialRelevant++;
        }
        if (initialRelevant == allChunks.size() || initialRelevant == 0) return allChunks;
        // 报价参数孤立块邻近扩展：仅相对初始命中数组扩张一跳（isRelevant 不回写），防止表格密集文档链式扩散为全量
        boolean[] expanded = new boolean[allChunks.size()];
        for (int idx = 0; idx < allChunks.size(); idx++) {
            if (isRelevant[idx]) continue;
            boolean neighbor = (idx > 0 && isRelevant[idx - 1]) || (idx + 1 < allChunks.size() && isRelevant[idx + 1]);
            String text = allChunks.get(idx).text();
            if (neighbor && (PRICE_PARAM_PATTERN.matcher(text).find() || text.contains("|"))) {
                expanded[idx] = true;
            }
        }
        List<DocumentChunk> filtered = new ArrayList<>();
        for (int idx = 0; idx < allChunks.size(); idx++) {
            if (isRelevant[idx] || expanded[idx]) filtered.add(allChunks.get(idx));
        }
        if (filtered.size() > initialRelevant) {
            log.info("评分 chunk 过滤邻近扩展: {} -> {} (含报价参数孤立块)", initialRelevant, filtered.size());
        }
        return filtered;
    }

    /** 候选提取调用：RuntimeException 重试恰好 1 次，两连败返回 null（调用方记跳过）。 */
    private ScoreCandidateOutput requestCandidatesWithRetry(
            String prompt, OpenAiBidAgentRequestConfig config) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return structuredOutputService.request(
                        prompt, ScoreCandidateOutput.class, config,
                        "AI structured response did not include score candidates");
            } catch (RuntimeException exception) {
                log.warn("评分候选 LLM 提取第 {} 次尝试失败: {}", attempt, exception.getMessage());
            }
        }
        return null;
    }

    /**
     * 完整性回补扫描（FR-005：WeightSumCheck 合计≠100 或存在未覆盖区域时触发）。
     * LLM 失败返回空列表（不阻断主流程，仅记录警告）。
     */
    public List<ScoreCandidate> recheckGaps(
            String fullTextExcerpt, List<ScoreCandidate> knownItems) {
        OpenAiBidAgentRequestConfig config = configurationResolver.resolve(USE_CASE);
        String sanitizedExcerpt = TenderIntakeTextProcessor.sanitizeUntrusted(fullTextExcerpt);
        String prompt = ScoreParsePrompts.buildGapRecheckPrompt(
                sanitizedExcerpt,
                ScoreCandidateMapper.summarizeKnownItems(knownItems),
                "footnotes, table-remarks, cross-page-refs");
        try {
            ScoreGapRecheckOutput output = structuredOutputService.request(
                    prompt, ScoreGapRecheckOutput.class, config,
                    "AI structured response did not include gap recheck result");
            if (output == null || output.missedItems == null) return List.of();
            return output.missedItems.stream()
                    .filter(candidate -> candidate != null && candidate.detail != null)
                    .map(candidate -> ScoreCandidateMapper.fromLlmCandidate(candidate, sanitizedExcerpt))
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("完整性回补扫描失败（忽略，不阻断主流程）: {}", exception.getMessage());
            return List.of();
        }
    }

    public ScoreAssessmentOutput assessObjective(
            String scoreItemDetail, java.math.BigDecimal weight, String bidDocExcerpt) {
        return assessObjective(scoreItemDetail, weight, bidDocExcerpt, List.of());
    }

    public ScoreAssessmentOutput assessObjective(
            String scoreItemDetail, java.math.BigDecimal weight, String bidDocExcerpt,
            List<DocumentImage> images) {
        return assessObjective(scoreItemDetail,weight,bidDocExcerpt,images,null);
    }
    public ScoreAssessmentOutput assessObjective(String scoreItemDetail,java.math.BigDecimal weight,String bidDocExcerpt,
            List<DocumentImage> images,com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiRequestBudget budget) {
        OpenAiBidAgentRequestConfig config = budget!=null&&budget.configuration()!=null?budget.configuration():configurationResolver.resolve(USE_CASE);
        if(budget!=null)config=config.forScoring(budget);
        boolean withImages = images != null && !images.isEmpty();
        String prompt = ScoreParsePrompts.buildObjectiveAssessmentPrompt(
                TenderIntakeTextProcessor.sanitizeUntrusted(scoreItemDetail),
                weight == null ? 0.0 : weight.doubleValue(),
                TenderIntakeTextProcessor.sanitizeUntrusted(bidDocExcerpt),
                withImages);
        List<MultimodalImage> multimodalImages = withImages
                ? images.stream().map(image -> new MultimodalImage(image.mimeType(), image.base64())).toList()
                : List.of();
        return structuredOutputService.request(
                prompt, multimodalImages, ScoreAssessmentOutput.class, config,
                "AI structured response did not include score assessment");
    }


    public ScoreAssessmentOutput assessSubjective(String scoreItemDetail, String bidDocExcerpt) {
        return assessSubjective(scoreItemDetail,bidDocExcerpt,null);
    }
    public ScoreAssessmentOutput assessSubjective(String scoreItemDetail,String bidDocExcerpt,
            com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiRequestBudget budget) {
        OpenAiBidAgentRequestConfig config = budget!=null&&budget.configuration()!=null?budget.configuration():configurationResolver.resolve(USE_CASE);
        if(budget!=null)config=config.forScoring(budget);
        String prompt = ScoreParsePrompts.buildSubjectiveSuggestionPrompt(
                TenderIntakeTextProcessor.sanitizeUntrusted(scoreItemDetail),
                TenderIntakeTextProcessor.sanitizeUntrusted(bidDocExcerpt));
        return structuredOutputService.request(
                prompt, ScoreAssessmentOutput.class, config,
                "AI structured response did not include subjective suggestion");
    }
    public OpenAiBidAgentRequestConfig scoringConfiguration(){return configurationResolver.resolve(USE_CASE);}
    private static void report(ProgressReporter progress, int value, String stage) {
        if (progress != null) progress.accept(value, stage);
    }
}
