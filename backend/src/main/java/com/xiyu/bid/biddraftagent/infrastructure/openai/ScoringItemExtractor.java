// Input: 评分标准章节文本（MarkdownScoreSectionLocator 提取的候选区域）
// Output: 评分项列表 List<ScoringCriterion>（正则兜底通道）
// Pos: biddraftagent/infrastructure/openai - 评分项正则兜底提取（单一职责）
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.biddraftagent.domain.PriceScoringKeywords;
import com.xiyu.bid.biddraftagent.domain.ScoringCriteriaClassificationPolicy;
import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评分项正则兜底提取器（spec 041 FR-001 召回二下游）。
 * <p>从 Markdown 评分标准章节文本中，基于行内正则匹配提取评分项。
 * 支持四种行格式：
 * <ul>
 *   <li>格式1：1 技术方案 商务部分 30分（编号 维度 指标 权重）</li>
 *   <li>格式2：1 技术方案 30分（编号 维度 权重）</li>
 *   <li>格式3：A1 技术方案 30分（字母编号 维度 权重）</li>
 *   <li>格式4：价格评分 30分（维度 分值，无编号）</li>
 * </ul>
 */
public final class ScoringItemExtractor {

    /** 归一化正则，与 TenderIntakeTextProcessor 保持一致。 */
    private static final String WHITESPACE_PATTERN =
            "[\\s\\u00A0\\u00AD\\u2000-\\u200D\\u2028-\\u202F\\u205F\\u2060\\u3000\\uFEFF]+";

    /** 下一章节标题格式：第X章、第X条。遇到则停止提取。 */
    private static final Pattern NEXT_CHAPTER_PATTERN =
            Pattern.compile("^第[一二三四五六七八九十百千万\\d]+[章节条]");

    /** 表头关键词，含这些词的行不提取。 */
    private static final List<String> HEADER_KEYWORDS = List.of(
            "序号", "评分项", "分值", "评审因素", "评审标准", "评分标准", "备注");

    /**
     * 编号正则：匹配行首的编号（1, 1.1, A1, B2 等）。
     * 编号必须后跟空白字符或行尾。
     */
    private static final Pattern ITEM_NUMBER_PATTERN = Pattern.compile(
            "^([A-Z]?\\d+(?:\\.\\d+)?)\\s+");

    /** 权重正则：匹配行尾的权重值（30分、30%、30）。 */
    private static final Pattern WEIGHT_PATTERN = Pattern.compile(
            "([0-9]+(?:\\.[0-9]+)?)\\s*[%分]?\\s*[)）]?\\s*$");

    /** 无编号格式的权重正则（维度+权重整行）。 */
    private static final Pattern NO_NUMBER_WEIGHT_PATTERN = Pattern.compile(
            "^(\\S{2,12})\\s*[（(]?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*[%分]\\s*[)）]?\\s*$");

    /** 报价参数行：仅为价格分计算的输入参数，非独立评分项 */
    private static final Pattern PRICE_PARAM_ROW_PATTERN = PriceScoringKeywords.PRICE_PARAM_ROW;

    private ScoringItemExtractor() {
    }

    /**
     * 从评分标准章节文本中正则提取评分项。
     *
     * @param sectionText 评分标准章节文本
     * @return 提取到的评分项列表；无匹配时返回空列表
     */
    public static List<ScoringCriterion> extract(String sectionText) {
        List<ScoringItemRow> rows = extractRaw(sectionText);
        ScoringCriteriaClassificationPolicy policy = new ScoringCriteriaClassificationPolicy();
        List<ScoringCriterion> criteria = new ArrayList<>();
        for (ScoringItemRow row : rows) {
            criteria.add(new ScoringCriterion(
                    row.itemNumber,
                    row.dimension,
                    row.indicator,
                    row.weight,
                    policy.classify(row.dimension)
            ));
        }
        return criteria;
    }

    /**
     * 从章节文本中提取原始行数据（不含 subType 分类）。
     * 包级可见以便测试验证中间结果。
     */
    static List<ScoringItemRow> extractRaw(String sectionText) {
        if (sectionText == null || sectionText.isBlank()) {
            return List.of();
        }

        List<ScoringItemRow> rows = new ArrayList<>();
        for (String rawLine : sectionText.split("\\R")) {
            if (rawLine == null || rawLine.isBlank()) {
                continue;
            }
            String normalized = normalizeSpaces(rawLine);
            if (isHeaderOrNoise(normalized)) {
                continue;
            }
            // 管道表格行分类处理（M-3 重构：提取为独立方法降低圈复杂度）
            String effectiveLine = normalized;
            if (normalized.contains("|")) {
                String pipeStripped = normalizeSpaces(normalized.replace("|", " "));
                PipeRowResult result = classifyPipeRow(pipeStripped);
                switch (result.action) {
                    case SKIP_PARAM -> { continue; }
                    case KEEP_AS_ROW -> { rows.add(result.row); continue; }
                    case USE_STRIPPED_TEXT -> effectiveLine = pipeStripped;
                }
            }
            // 章节标题行（如"第二章 评标办法"）同时匹配 NEXT_CHAPTER_PATTERN 和评分别名，
            // 必须先排除评分别名，否则提取器在标题行就 break 了
            if (NEXT_CHAPTER_PATTERN.matcher(effectiveLine).find()
                    && !containsScoringAlias(effectiveLine)) {
                break;
            }

            ScoringItemRow row = matchScoringLine(effectiveLine);
            if (row == null) {
                row = matchNoNumberLine(effectiveLine);
            }
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    /** 管道行分类结果 */
    private enum PipeRowAction { KEEP_AS_ROW, SKIP_PARAM, USE_STRIPPED_TEXT }

    private record PipeRowResult(PipeRowAction action, ScoringItemRow row) {
        static PipeRowResult skip() { return new PipeRowResult(PipeRowAction.SKIP_PARAM, null); }
        static PipeRowResult keep(ScoringItemRow row) { return new PipeRowResult(PipeRowAction.KEEP_AS_ROW, row); }
        static PipeRowResult passthrough() { return new PipeRowResult(PipeRowAction.USE_STRIPPED_TEXT, null); }
    }

    /**
     * 管道行分类：去掉 | 后判定行类型。
     * <p>三道判据（按优先级）：
     * <ol>
     *   <li>章节别名守卫：行内含评标办法/评分标准等章节别名且无 % 尾值时保留</li>
     *   <li>参数行与主项行区分：命中 PRICE_PARAM_ROW 时，若提取出的维度含报价维度词元（报价/价格）
     *       视为主项行保留（"1 价格分计算方法 30%" 形态）；否则作为纯参数行跳过（"2 BP% 80%"）</li>
     *   <li>其余命中评分模式的行正常保留；未命中的透传文本供 LLM</li>
     * </ol>
     */
    private static PipeRowResult classifyPipeRow(String pipeStripped) {
        ScoringItemRow pipeRow = matchScoringLine(pipeStripped);
        if (pipeRow == null) {
            pipeRow = matchNoNumberLine(pipeStripped);
        }

        // ① 章节别名守卫：行内含评标办法/评分标准等章节别名且无 % 尾值时优先保留
        boolean aliasBacked = containsScoringAlias(pipeStripped) && !pipeStripped.trim().endsWith("%");
        if (pipeRow != null && aliasBacked) {
            return PipeRowResult.keep(pipeRow);
        }

        // ② 参数行判定：命中参数行模式但维度含报价维度词元时视为主项行保留
        if (PRICE_PARAM_ROW_PATTERN.matcher(pipeStripped).find()) {
            boolean isPriceMainItem = pipeRow != null && pipeRow.dimension() != null
                    && PriceScoringKeywords.PRICE_DIM_TOKENS.stream()
                            .anyMatch(pipeRow.dimension()::contains);
            if (isPriceMainItem) {
                return PipeRowResult.keep(pipeRow);
            }
            return PipeRowResult.skip();
        }

        // ③ 其余命中评分模式的行正常保留
        if (pipeRow != null) {
            return PipeRowResult.keep(pipeRow);
        }

        return PipeRowResult.passthrough();
    }

    private static ScoringItemRow matchScoringLine(String normalized) {
        Matcher numMatcher = ITEM_NUMBER_PATTERN.matcher(normalized);
        if (!numMatcher.find()) {
            return null;
        }
        String itemNumber = numMatcher.group(1);
        String remainder = normalized.substring(numMatcher.end()).trim();

        // 从 remainder 尾部提取权重
        BigDecimal weight = null;
        Matcher weightMatcher = WEIGHT_PATTERN.matcher(remainder);
        if (weightMatcher.find()) {
            weight = new BigDecimal(weightMatcher.group(1));
            remainder = remainder.substring(0, weightMatcher.start()).trim();
            // 去除尾部括号和分隔符
            remainder = remainder.replaceAll("[（(\\s]+$", "").trim();
        }

        // remainder 此时是 "维度 [指标]" 或 "维度"
        if (remainder.isEmpty()) {
            return null;
        }
        String[] parts = remainder.split("\\s+", 2);
        String dimension = parts[0];
        String indicator = parts.length > 1 ? parts[1] : null;

        // 维度至少 2 字，排除单字噪声
        if (dimension.length() < 2) {
            return null;
        }
        return new ScoringItemRow(itemNumber, dimension, indicator, weight);
    }

    private static ScoringItemRow matchNoNumberLine(String normalized) {
        Matcher m = NO_NUMBER_WEIGHT_PATTERN.matcher(normalized);
        if (!m.matches()) {
            return null;
        }
        String dimension = m.group(1).trim();
        BigDecimal weight = new BigDecimal(m.group(2));
        return new ScoringItemRow(null, dimension, null, weight);
    }

    private static boolean isHeaderOrNoise(String normalized) {
        // 管道表格行（报价评分价格分长表格）长度常超 100（<br>、| 分隔），需放宽阈值；
        // 非表格长行仍按 100 截断防噪声。判据用 startsWith("|") 而非 contains("|")，
        // 防止自然语言中的 | 误触发 600 阈值。
        boolean isPipeTableRow = normalized.trim().startsWith("|");
        if (normalized.length() > (isPipeTableRow ? 600 : 100)) {
            return true;
        }
        for (String keyword : HEADER_KEYWORDS) {
            if (normalized.contains(keyword) && normalized.length() <= 20) {
                return true;
            }
        }
        return false;
    }

    /** 检查行是否包含评分标准章节别名（用于排除章节标题行的 NEXT_CHAPTER 判定）。 */
    private static boolean containsScoringAlias(String normalized) {
        for (String alias : ScoringSectionAliases.ALL) {
            if (normalized.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeSpaces(String text) {
        if (text == null) return "";
        // 归一化所有 Unicode 空白为单个半角空格
        return text.replaceAll(WHITESPACE_PATTERN, " ").trim();
    }

    /** 中间结果行，包级可见以便测试。 */
    record ScoringItemRow(
            String itemNumber,
            String dimension,
            String indicator,
            BigDecimal weight
    ) {}
}
