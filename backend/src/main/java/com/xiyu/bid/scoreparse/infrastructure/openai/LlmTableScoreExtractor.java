// Input: 单个 Markdown 评分表格块或包含多个表格的 Markdown 全文
// Output: List<ScoreCandidate>（整表结构化抽取并经原文对齐后的候选列表）
// Pos: scoreparse/infrastructure/openai — 表格级 LLM 结构化抽取服务
package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreOriginalTextAligner;
import com.xiyu.bid.scoreparse.domain.ScoreParseConstants;
import com.xiyu.bid.scoreparse.infrastructure.structure.MarkdownScoreSectionLocator;
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
 * <p>当启发式列推断未命中时，在评分章节或合法表格范围内通过整表空间语义结构化抽取评分项。
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
    private static final Pattern CONTRACT_ASSESSMENT_HEADER = ScoreParseConstants.CONTRACT_ASSESSMENT_PATTERN;
    private static final Pattern PROCEDURE_TABLE_MARKER = Pattern.compile("编列内容|编列规定|条款内容|前附表|须知前附表");
    private static final Pattern SCORE_COLUMN_MARKER = Pattern.compile("评分因素|评分标准|评审因素|评审标准|分值|得分");

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
     * <p>优先在定位到的评分标准章节内抽取，避免混入前附表、合同条款等无关表格。
     */
    public List<ScoreCandidate> extractFromDocument(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return Collections.emptyList();
        }

        // 1. 优先定位评分标准章节（如第三章 评标办法），仅抽取章节内的评分表格
        List<MarkdownScoreSectionLocator.ScoreSection> sections = MarkdownScoreSectionLocator.locate(markdown);
        if (!sections.isEmpty()) {
            List<ScoreCandidate> candidates = extractFromSections(sections);
            if (!candidates.isEmpty()) {
                return candidates;
            }
        }

        // 2. 章节定位未命中时，在排除须知前附表和合同考核表后抽取合法表格
        return extractFromRawMarkdown(markdown);
    }

    private List<ScoreCandidate> extractFromSections(List<MarkdownScoreSectionLocator.ScoreSection> sections) {
        List<ScoreCandidate> allCandidates = new ArrayList<>();
        for (MarkdownScoreSectionLocator.ScoreSection section : sections) {
            for (String table : extractRawTableBlocks(section.content())) {
                if (isScoreTableCandidate(table)) {
                    try {
                        allCandidates.addAll(extractFromTable(table));
                    } catch (RuntimeException e) {
                        log.warn("评分章节内表格 LLM 抽取失败，跳过: {}", e.getMessage());
                    }
                }
            }
        }
        return allCandidates;
    }

    private List<ScoreCandidate> extractFromRawMarkdown(String markdown) {
        List<ScoreCandidate> allCandidates = new ArrayList<>();
        for (String table : extractRawTableBlocks(markdown)) {
            if (isScoreTableCandidate(table)) {
                try {
                    allCandidates.addAll(extractFromTable(table));
                } catch (RuntimeException e) {
                    log.warn("单个评分表格 LLM 抽取失败，跳过: {}", e.getMessage());
                }
            }
        }
        return allCandidates;
    }

    private static boolean isScoreTableCandidate(String table) {
        if (table == null || !SCORE_SEMANTIC_FAST_FILTER.matcher(table).find()) {
            return false;
        }
        String firstLine = table.lines().findFirst().orElse("");
        if (CONTRACT_ASSESSMENT_HEADER.matcher(firstLine).find()
                && !ScoreParseConstants.SCORE_HEADER_ESCAPE_PATTERN.matcher(firstLine).find()) {
            return false;
        }
        if (PROCEDURE_TABLE_MARKER.matcher(firstLine).find()) {
            return SCORE_COLUMN_MARKER.matcher(firstLine).find();
        }
        return true;
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
                "TABLE_LLM",
                item.parentDim,
                "UNKNOWN",
                item.deliverableType,
                item.scoringMode
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
