// Input: 招标文件全文/Markdown + structuredMetadata + 进度回调
// Output: List<ScoreCandidate>（四路召回合并前的候选池）/ 回补候选
// Pos: scoreparse/infrastructure/openai — LLM 编排（召回三/四 + 完整性回补）
// 维护声明: 维护者按项目SOP；spec 041 FR-001 四路召回、FR-005 二次回补；候选映射拆至 ScoreCandidateMapper（2026-08-19 满足 300 行预算）
package com.xiyu.bid.scoreparse.infrastructure.openai;

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
import com.xiyu.bid.scoreparse.domain.PriceScoringKeywords;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.infrastructure.structure.MarkdownScoreSectionLocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/** LLM 编排器（spec 041 FR-001 四路召回，FR-005 回补）- 详见类头维护声明 */
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

    public interface ProgressReporter extends BiConsumer<Integer, String> {}
    public RecallOutcome recallCandidates(
            String fullText, String structuredMetadata, ProgressReporter progress) {
        report(progress, 5, "召回一/二：关键词正则规则 + 文档结构定位");
        List<ScoreCandidate> candidates = new ArrayList<>();
        candidates.addAll(recallLane1RegexRules(fullText));
        candidates.addAll(recallLane2DocumentStructure(fullText));

        report(progress, 15, "召回三/四：评分规则语义切片 + LLM 全文语义提取");
        candidates.addAll(recallLane3ScoreSemanticPatterns(fullText));
        RecallOutcome lane4 = recallLane4LlmFullText(fullText, structuredMetadata, progress);
        candidates.addAll(lane4.candidates());
        return new RecallOutcome(candidates, lane4.skippedChunks());
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
        List<String> semanticParagraphs = ScoreDocExcerptExtractor.extractSemanticScoreParagraphs(fullText);
        for (String paragraph : semanticParagraphs) {
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
                alignedCount += countAligned(chunkCandidates, chunkText);
                candidates.addAll(chunkCandidates);
            }
            report(progress, 15 + llmProgressSpan * (i + 1) / chunks.size(),
                    "召回三/四：LLM 语义提取 " + (i + 1) + "/" + chunks.size());
        }
        log.info("召回四（LLM 全文语义）完成: chunks={}/{}, count={}, skipped={}, aligned={}/{}",
                chunks.size(), allChunks.size(), candidates.size(), skipped, alignedCount, candidates.size());
        return new RecallOutcome(candidates, skipped);
    }

    private List<DocumentChunk> filterRelevantChunks(List<DocumentChunk> allChunks) {
        if (allChunks.size() <= 4) {
            return allChunks;
        }
        boolean[] isRelevant = new boolean[allChunks.size()];
        int initialRelevant = 0;
        for (int idx = 0; idx < allChunks.size(); idx++) {
            DocumentChunk chunk = allChunks.get(idx);
            boolean pathMatches = chunk.sectionPath() != null && chunk.sectionPath().stream()
                    .anyMatch(p -> p != null && SCORE_CHUNK_KEYWORD_PATTERN.matcher(p).find());
            boolean textMatches = SCORE_CHUNK_KEYWORD_PATTERN.matcher(chunk.text()).find();
            boolean priceParam = PRICE_PARAM_PATTERN.matcher(chunk.text()).find();
            isRelevant[idx] = pathMatches || textMatches || priceParam;
            if (isRelevant[idx]) initialRelevant++;
        }
        if (initialRelevant == allChunks.size() || initialRelevant == 0) return allChunks;
        // 报价参数孤立块邻近扩展：同一表格跨 chunk 导致参数孤立，若相邻已命中则保留
        for (int idx = 0; idx < allChunks.size(); idx++) {
            if (isRelevant[idx]) continue;
            boolean neighborRelevant = (idx > 0 && isRelevant[idx - 1]) || (idx + 1 < allChunks.size() && isRelevant[idx + 1]);
            if (neighborRelevant) {
                String txt = allChunks.get(idx).text();
                if (PRICE_PARAM_PATTERN.matcher(txt).find() || txt.contains("|")) {
                    isRelevant[idx] = true;
                }
            }
        }
        List<DocumentChunk> filtered = new ArrayList<>();
        for (int idx = 0; idx < allChunks.size(); idx++) {
            if (isRelevant[idx]) filtered.add(allChunks.get(idx));
        }
        if (filtered.size() > initialRelevant) {
            log.info("评分 chunk 过滤邻近扩展: {} -> {} (含报价参数孤立块)", initialRelevant, filtered.size());
        }
        return filtered;
    }

    /** 对齐成功数（detail 为 chunk 原文子串） */
    private static int countAligned(List<ScoreCandidate> chunkCandidates, String chunkText) {
        return (int) chunkCandidates.stream()
                .filter(candidate -> chunkText.contains(candidate.detail()))
                .count();
    }

    /** 重试 1 次，两连败返回 null */
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

    /** 完整性回补（FR-005） */
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
            if (output == null || output.missedItems == null) {
                return List.of();
            }
            return output.missedItems.stream()
                    .filter(candidate -> candidate != null && candidate.detail != null)
                    .map(candidate -> ScoreCandidateMapper.fromLlmCandidate(candidate, sanitizedExcerpt))
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("完整性回补扫描失败（忽略，不阻断主流程）: {}", exception.getMessage());
            return List.of();
        }
    }

    /** 阶段 2 客观项打分（契约 §4，守卫由调用方执行） */
    public ScoreAssessmentOutput assessObjective(
            String scoreItemDetail, java.math.BigDecimal weight, String bidDocExcerpt) {
        return assessObjective(scoreItemDetail, weight, bidDocExcerpt, List.of());
    }

    /** 阶段 2 多模态过载（spec 048 T017） */
    public ScoreAssessmentOutput assessObjective(
            String scoreItemDetail, java.math.BigDecimal weight, String bidDocExcerpt,
            List<DocumentImage> images) {
        OpenAiBidAgentRequestConfig config = configurationResolver.resolve(USE_CASE);
        boolean withImages = images != null && !images.isEmpty();
        String prompt = ScoreParsePrompts.buildObjectiveAssessmentPrompt(
                TenderIntakeTextProcessor.sanitizeUntrusted(scoreItemDetail),
                weight.doubleValue(),
                TenderIntakeTextProcessor.sanitizeUntrusted(bidDocExcerpt),
                withImages);
        List<MultimodalImage> multimodalImages = withImages
                ? images.stream()
                        .map(image -> new MultimodalImage(image.mimeType(), image.base64()))
                        .toList()
                : List.of();
        return structuredOutputService.request(
                prompt, multimodalImages, ScoreAssessmentOutput.class, config,
                "AI structured response did not include score assessment");
    }

    /** 阶段 2 主观项建议（SC-003 零泄漏） */
    public ScoreAssessmentOutput assessSubjective(String scoreItemDetail, String bidDocExcerpt) {
        OpenAiBidAgentRequestConfig config = configurationResolver.resolve(USE_CASE);
        String prompt = ScoreParsePrompts.buildSubjectiveSuggestionPrompt(
                TenderIntakeTextProcessor.sanitizeUntrusted(scoreItemDetail),
                TenderIntakeTextProcessor.sanitizeUntrusted(bidDocExcerpt));
        return structuredOutputService.request(
                prompt, ScoreAssessmentOutput.class, config,
                "AI structured response did not include subjective suggestion");
    }

    private static void report(ProgressReporter progress, int value, String stage) {
        if (progress != null) {
            progress.accept(value, stage);
        }
    }
}
