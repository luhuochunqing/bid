// Input: 评分标准章节文本（由 ScoringSectionLocator 定位）
// Output: List<ScoringCriterion> 正则提取的评分项列表
// Pos: biddraftagent/infrastructure/openai - 评分项正则兜底提取（单一职责）
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.biddraftagent.domain.ScoringCriteriaClassificationPolicy;
import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评分项正则兜底提取器。
 *
 * <p>设计动机：招标文件中的评分表通常是结构化的（编号+维度+权重），
 * 属于正则可稳定命中的格式。遵循 regex-vs-llm-structured-text 原则：
 * 结构化表格行走正则，AI 做语义补充。
 *
 * <p>对标 {@link PurchaserNameExtractor} 的设计模式：
 * <ul>
 *   <li>按行扫描，归一化后正则匹配</li>
 *   <li>提取结构化字段（编号、维度、权重）</li>
 *   <li>复用 {@link ScoringCriteriaClassificationPolicy} 做子类型分类</li>
 * </ul>
 *
 * <p>支持 4 种常见格式：
 * <ul>
 *   <li>格式1：1 价格 30（编号 维度 分值）</li>
 *   <li>格式2：1.1 技术方案 方案完整性 30分（编号 维度 指标 分值）</li>
 *   <li>格式3：A1 技术方案（30分）（编号 维度+括号分值）</li>
 *   <li>格式4：价格评分 30分（维度 分值，无编号）</li>
 * </ul>
 */
final class ScoringItemExtractor {

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

    private ScoringItemExtractor() {
    }

    /**
     * 从评分标准章节文本中正则提取评分项。
     *
     * @param sectionText 评分标准章节文本
     * @return 提取到的评分项列表；无匹配时返回空列表
     */
    static List<ScoringCriterion> extract(String sectionText) {
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
            // 章节标题行（如"第二章 评标办法"）同时匹配 NEXT_CHAPTER_PATTERN 和评分别名，
            // 必须先排除评分别名，否则提取器在标题行就 break 了
            if (NEXT_CHAPTER_PATTERN.matcher(normalized).find()
                    && !containsScoringAlias(normalized)) {
                break;
            }

            ScoringItemRow row = matchScoringLine(normalized);
            if (row == null) {
                row = matchNoNumberLine(normalized);
            }
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
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
        if (normalized.length() > 100) {
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
