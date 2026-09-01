// Input: 单个 Markdown 评分表格块或包含多个表格的 Markdown 全文
// Output: List<ScoreCandidate>（整表结构化抽取并经原文对齐后的候选列表）
// Pos: scoreparse/infrastructure/openai — 表格级 LLM 结构化抽取服务
package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreOriginalTextAligner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 评分表格整表 LLM 结构化抽取器。
 * <p>替代传统的行级正则特征工程与启发式列推断，直接基于整表空间语义理解输出评分项候选。
 */
@Service
@Profile("!e2e")
@Slf4j
public class LlmTableScoreExtractor {

    private static final String USE_CASE = "score table extraction";
    private static final int CODE_MAX_CHARS = 50;
    private static final int DIM_MAX_CHARS = 200;
    private static final int SOURCE_TEXT_MAX_CHARS = 500;

    private static final Pattern SCORE_SEMANTIC_FAST_FILTER = Pattern.compile(
            "评分|评审|评标|分值|得分|打分|加分|扣分|标准|细则|权重|比例|分"
    );

    private final OpenAiStructuredOutputService structuredOutputService;
    private final OpenAiBidAgentConfigurationResolver configurationResolver;

    public LlmTableScoreExtractor(
            OpenAiStructuredOutputService structuredOutputService,
            OpenAiBidAgentConfigurationResolver configurationResolver) {
        this.structuredOutputService = structuredOutputService;
        this.configurationResolver = configurationResolver;
    }

    /**
     * 从 Markdown 全文中定位评分表格块并依次进行 LLM 结构化抽取。
     */
    public List<ScoreCandidate> extractFromDocument(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return Collections.emptyList();
        }
        List<String> rawTables = extractRawTableBlocks(markdown);
        if (rawTables.isEmpty()) {
            return Collections.emptyList();
        }

        List<ScoreCandidate> allCandidates = new ArrayList<>();
        for (String table : rawTables) {
            if (SCORE_SEMANTIC_FAST_FILTER.matcher(table).find()) {
                try {
                    allCandidates.addAll(extractFromTable(table));
                } catch (RuntimeException e) {
                    log.warn("单个评分表格 LLM 抽取失败，跳过: {}", e.getMessage());
                }
            }
        }
        return allCandidates;
    }

    /**
     * 对单个 Markdown 表格块进行整表抽取。
     */
    public List<ScoreCandidate> extractFromTable(String tableMarkdown) {
        if (tableMarkdown == null || tableMarkdown.isBlank()) {
            return Collections.emptyList();
        }

        String prompt = TableScoreExtractionPrompt.build(tableMarkdown);
        try {
            OpenAiBidAgentRequestConfig config = configurationResolver.resolve(USE_CASE);
            TableScoreExtractionOutput output = structuredOutputService.request(
                    prompt,
                    TableScoreExtractionOutput.class,
                    config,
                    "AI response did not include table score extraction output"
            );

            if (output == null || !output.isScoreTable || output.items == null || output.items.isEmpty()) {
                if (output != null && !output.isScoreTable) {
                    log.info("LLM 判定非评分表格: type={}, reason={}", output.tableType, output.nonScoreReason);
                }
                return Collections.emptyList();
            }

            List<ScoreCandidate> candidates = new ArrayList<>();
            for (TableScoreExtractionOutput.TableScoreItem item : output.items) {
                if (item == null || item.isSummaryRow || item.weight == null || item.weight <= 0
                        || item.detail == null || item.detail.isBlank()) {
                    continue;
                }
                candidates.add(toCandidate(item, tableMarkdown));
            }
            log.info("LLM 表格抽取成功: tableType={}, 提取项数={}", output.tableType, candidates.size());
            return candidates;
        } catch (RuntimeException e) {
            log.warn("LLM 表格抽取异常: {}", e.getMessage());
            throw e;
        }
    }

    public static List<String> extractRawTableBlocks(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return Collections.emptyList();
        }
        List<String> tables = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : markdown.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("|")) {
                current.append(trimmed).append("\n");
            } else if (current.length() > 0) {
                flushTable(current, tables);
            }
        }
        flushTable(current, tables);
        return tables;
    }

    private static void flushTable(StringBuilder current, List<String> tables) {
        if (current.length() > 0) {
            String tableStr = current.toString().trim();
            if (tableStr.lines().count() >= 2) {
                tables.add(tableStr);
            }
            current.setLength(0);
        }
    }

    private ScoreCandidate toCandidate(TableScoreExtractionOutput.TableScoreItem item, String sourceTable) {
        String rawDim = item.dim != null ? item.dim.trim() : "";
        if (item.parentDim != null && !item.parentDim.isBlank()) {
            String pDim = item.parentDim.trim();
            rawDim = rawDim.isBlank() ? pDim : (rawDim.contains(pDim) ? rawDim : pDim + "/" + rawDim);
        }

        String rawDetail = item.detail.trim();
        String alignedDetail = ScoreOriginalTextAligner.align(sourceTable, rawDetail);
        String alignedDim = ScoreOriginalTextAligner.align(sourceTable, rawDim);

        return new ScoreCandidate(
                truncate(item.code, CODE_MAX_CHARS),
                truncate(alignedDim != null ? alignedDim : rawDim, DIM_MAX_CHARS),
                alignedDetail != null ? alignedDetail : rawDetail,
                BigDecimal.valueOf(item.weight),
                item.scoreTypeGuess != null && !item.scoreTypeGuess.isBlank() ? item.scoreTypeGuess : "OBJECTIVE",
                item.contextNote != null ? item.contextNote.trim() : "",
                alignedDetail != null ? alignedDetail : truncate(sourceTable, SOURCE_TEXT_MAX_CHARS),
                "",
                "TABLE_LLM"
        );
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= maxChars ? trimmed : trimmed.substring(0, maxChars);
    }
}
