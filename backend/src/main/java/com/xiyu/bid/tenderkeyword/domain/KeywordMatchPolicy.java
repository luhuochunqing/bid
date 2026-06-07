// Input: keyword matching inputs (searchText, keywords, logicOperator)
// Output: MatchResult value indicating match outcome and matched keywords
// Pos: Pure Core/关键词匹配策略
package com.xiyu.bid.tenderkeyword.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 纯核心：标讯关键词匹配策略
 *
 * <p>根据关键词组合（AND/OR）对标讯搜索文本进行匹配判断。
 * 不依赖数据库、API 或框架。
 */
public final class KeywordMatchPolicy {

    private KeywordMatchPolicy() {
    }

    public static final String OPERATOR_AND = "AND";
    public static final String OPERATOR_OR = "OR";
    private static final Set<String> VALID_OPERATORS = Set.of(OPERATOR_AND, OPERATOR_OR);

    /**
     * 匹配结果
     */
    public record MatchResult(boolean matched, List<String> matchedKeywords) {
        public static MatchResult noMatch() {
            return new MatchResult(false, List.of());
        }
        public static MatchResult match(List<String> matchedKeywords) {
            return new MatchResult(true, List.copyOf(matchedKeywords));
        }
    }

    /**
     * 判断标讯是否匹配关键词组合
     *
     * @param searchText    标讯搜索文本（已归一化的小写文本）
     * @param keywords      关键词列表
     * @param logicOperator AND（全部匹配）/ OR（任一匹配）
     * @return 匹配结果
     */
    public static MatchResult evaluate(String searchText, List<String> keywords, String logicOperator) {
        if (searchText == null || keywords == null || keywords.isEmpty()) {
            return MatchResult.noMatch();
        }

        String normalizedSearch = searchText.toLowerCase(Locale.ROOT);
        List<String> normalizedKeywords = keywords.stream()
            .filter(k -> k != null && !k.isBlank())
            .map(k -> k.toLowerCase(Locale.ROOT))
            .toList();

        if (normalizedKeywords.isEmpty()) {
            return MatchResult.noMatch();
        }

        String effectiveOperator = (logicOperator != null && VALID_OPERATORS.contains(logicOperator))
            ? logicOperator
            : OPERATOR_OR;

        if (OPERATOR_AND.equals(effectiveOperator)) {
            // AND: 所有关键词都必须匹配
            List<String> matched = new ArrayList<>();
            for (String keyword : normalizedKeywords) {
                if (normalizedSearch.contains(keyword)) {
                    matched.add(keyword);
                } else {
                    return MatchResult.noMatch();
                }
            }
            return MatchResult.match(matched);
        } else {
            // OR: 任一关键词匹配即可
            for (String keyword : normalizedKeywords) {
                if (normalizedSearch.contains(keyword)) {
                    return MatchResult.match(List.of(keyword));
                }
            }
            return MatchResult.noMatch();
        }
    }

    /**
     * 验证关键词逻辑操作符
     */
    public static boolean isValidOperator(String operator) {
        return operator != null && VALID_OPERATORS.contains(operator);
    }
}
