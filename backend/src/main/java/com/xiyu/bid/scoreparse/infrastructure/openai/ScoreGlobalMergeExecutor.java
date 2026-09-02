// Input: 去噪候选池 + 评分章节原文（application 编排层传入）
// Output: 全局归并后的最终候选清单（LLM 两连败/前置不足返回 empty = fail-open 信号）
// Pos: scoreparse/infrastructure/openai — LLM 归并裁决调用（spec 055 FR-1/FR-3）
// 维护声明: 维护者按项目SOP；契约 llm-global-merge-schema.md §5；编排降级语义见 ScoreGlobalMergeOrchestrator
package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 全局归并裁决执行器（spec 055 两阶段架构的裁决阶段）。
 * <p>单次 LLM 调用（重试恰好 1 次）；候选池 &lt;2 或章节原文缺失跳过调用返回 empty；
 * 两连败返回 empty，由编排层降级回启发式合并结果（fail-open，宪法 VII）。
 */
@Service
@Profile("!e2e")
@Slf4j
public class ScoreGlobalMergeExecutor {

    private static final String USE_CASE = "score parse analysis";
    /** 池摘要中单条 detail 截断上界（防止单条异常超大文本爆炸，正常细则全量保留） */
    static final int POOL_DETAIL_TRUNCATE = 4000;

    private final OpenAiStructuredOutputService structuredOutputService;
    private final OpenAiBidAgentConfigurationResolver configurationResolver;

    public ScoreGlobalMergeExecutor(
            OpenAiStructuredOutputService structuredOutputService,
            OpenAiBidAgentConfigurationResolver configurationResolver) {
        this.structuredOutputService = structuredOutputService;
        this.configurationResolver = configurationResolver;
    }

    /** 前置不足或 LLM 两连败返回 empty（编排层视为"保持启发式结果"信号） */
    public List<ScoreCandidate> resolve(List<ScoreCandidate> pool, String sectionText) {
        return resolve(pool, sectionText, null);
    }

    /**
     * 含词汇注册表 escape-hatch 的归并裁决。
     * @param vocabularyExamples {@link com.xiyu.bid.scoreparse.domain.ScoreVocabularyRegistry#toPromptExamples} 输出，null 表示不注入
     */
    public List<ScoreCandidate> resolve(List<ScoreCandidate> pool, String sectionText, String vocabularyExamples) {
        if (pool == null || pool.size() < 2 || sectionText == null || sectionText.isBlank()) {
            return List.of();
        }
        String prompt = ScoreParsePrompts.buildGlobalMergePrompt(summarizePool(pool), sectionText, vocabularyExamples);
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                // config 解析在 try 内：resolver 缺配置的异常同样走 fail-open（评审 M2）
                OpenAiBidAgentRequestConfig config = configurationResolver.resolve(USE_CASE);
                ScoreGlobalMergeOutput output = structuredOutputService.request(
                        prompt, ScoreGlobalMergeOutput.class, config,
                        "AI structured response did not include global merge result");
                logMergeSummary(output);
                return toCandidates(output);
            } catch (RuntimeException exception) {
                log.warn("全局归并 LLM 第 {} 次尝试失败: {}", attempt, exception.getMessage());
            }
        }
        return List.of();
    }

    /** FR-6：LLM 自报决策摘要落日志（编排层计数之外的过程可观测） */
    private static void logMergeSummary(ScoreGlobalMergeOutput output) {
        if (output != null && output.summary != null) {
            log.info("全局归并 LLM 自报摘要: mergedGroupCount={}, discardedCandidateCount={}, notes={}",
                    output.summary.mergedGroupCount, output.summary.discardedCandidateCount,
                    output.summary.notes);
        }
    }

    /** 池摘要 JSON：idx + code/dim/detail(截断)/weight，供 LLM 身份识别 */
    static String summarizePool(List<ScoreCandidate> pool) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < pool.size(); i++) {
            ScoreCandidate candidate = pool.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"idx\":").append(i)
                    .append(",\"code\":\"").append(escape(candidate.code())).append('"')
                    .append(",\"dim\":\"").append(escape(candidate.dim())).append('"')
                    .append(",\"detail\":\"").append(escape(truncate(candidate.detail()))).append('"')
                    .append(",\"weight\":").append(candidate.weight() == null
                            ? "null" : candidate.weight().toPlainString())
                    .append('}');
        }
        return sb.append(']').toString();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= POOL_DETAIL_TRUNCATE
                ? text : text.substring(0, POOL_DETAIL_TRUNCATE);
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ")
                .replaceAll("[\\x00-\\x1f]", " ");
    }

    private static List<ScoreCandidate> toCandidates(ScoreGlobalMergeOutput output) {
        if (output == null || output.items == null) {
            return List.of();
        }
        return output.items.stream()
                .filter(item -> item != null && item.detail != null && !item.detail.isBlank())
                .map(item -> new ScoreCandidate(
                        item.code == null ? "" : item.code,
                        resolveDim(item.dim, item.detail),
                        item.detail,
                        item.weight == null ? null : BigDecimal.valueOf(item.weight),
                        item.scoreTypeGuess, null,
                        item.sourceText == null ? "" : item.sourceText,
                        null, "GLOBAL_MERGE"))
                .toList();
    }

    private static String resolveDim(String dim, String detail) {
        if (dim != null && !dim.isBlank()) {
            return dim.trim();
        }
        if (detail != null && !detail.isBlank()) {
            String trimmed = detail.trim();
            return trimmed.length() > 50 ? trimmed.substring(0, 50) : trimmed;
        }
        return "评分项";
    }
}
