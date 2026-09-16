// Input: 项目资格项列表 + 标书原文
// Output: 合并后的实质业绩条款文本（去重、限量、超长截断）
// Pos: casework/domain/policy/ - 资格项业绩条款收集纯核心策略（FP-Java 无外部依赖）
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.casework.domain.policy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.hasText;

/**
 * 纯核心：从资格项列表与标书资格审查/初步评审章节收集实质业绩条款。
 *
 * <p>遵循 FP-Java 规范：纯逻辑、无 Spring/DB 依赖、无副作用、100% 可单测。</p>
 *
 * <p>防泛化约束：仅限定在资格审查/初步评审边界内提取，坚决阻断全文档非资格区域噪声；
 * 条款条数与总长度受上限保护，避免打分上下文与关键词槽位膨胀。</p>
 */
public final class QualificationClauseCollector {

    /** 资格项章节正则锚点（限定在资格审查/初步评审范围，坚决阻断全文档非资格区域噪声） */
    private static final Pattern QUALIFICATION_SECTION_PATTERN = Pattern.compile(
            "(?:(?:初步评审|资格审查|资格评审|资格性审查|供应商资格|资格条件|投标人资格|申请人资格)[^\\n]*)([\\s\\S]*?)(?=(?:\\n\\s*(?:[一二三四五六七八九十\\d]+[、.\\s]*)?(?:详细评审|技术需求|商务要求|投标文件格式|第四章|第五章|第六章|第七章)|$))",
            Pattern.CASE_INSENSITIVE
    );

    /** 资格项中属于业绩条款的判定正则（必须同时出现业绩或类似供货要求，以及时间/数量/金额等实质条件） */
    private static final Pattern PERF_CLAUSE_PATTERN = Pattern.compile(
            "[^。；;\\n]*?(?:业绩|类似项目|供货记录|合同)[^。；;\\n]*?(?:年|项|万|元|发票|电商|供应|不低于|不少于|累计)[^。；;\\n]*",
            Pattern.CASE_INSENSITIVE
    );

    /** 合并条款上限（条数）+ 单条款字符上限：防止超长/超多条款膨胀，同时保留每条金额完整 */
    private static final int MAX_MERGED_CLAUSES = 8, MAX_CLAUSE_CHARS = 1000;

    private QualificationClauseCollector() {}

    /**
     * 合并全部实质业绩条款（资格项列表 + 资格审查/初步评审章节），返回以换行连接的文本；无条款时返回空串。
     */
    public static String collectMerged(List<String> qualificationRequirements, String rawTenderText) {
        Set<String> clauses = new LinkedHashSet<>();
        if (qualificationRequirements != null) {
            for (String requirement : qualificationRequirements) {
                String clause = substantiveClauseOrNull(requirement);
                if (clause != null && !isDuplicate(clauses, clause)) {
                    clauses.add(clause);
                }
            }
        }
        if (hasText(rawTenderText)) {
            for (String clause : collectFromRawSections(rawTenderText)) {
                if (!isDuplicate(clauses, clause)) {
                    clauses.add(clause);
                }
            }
        }
        if (clauses.isEmpty()) {
            return "";
        }
        return clauses.stream()
                .limit(MAX_MERGED_CLAUSES)
                .map(QualificationClauseCollector::truncateClause)
                .collect(Collectors.joining("\n"));
    }

    private static List<String> collectFromRawSections(String rawTenderText) {
        List<String> clauses = new ArrayList<>();
        Matcher sectionMatcher = QUALIFICATION_SECTION_PATTERN.matcher(rawTenderText);
        while (sectionMatcher.find() && clauses.size() < MAX_MERGED_CLAUSES) {
            String sectionContent = sectionMatcher.group(1);
            if (!hasText(sectionContent)) {
                continue;
            }
            for (String para : sectionContent.split("\\n+")) {
                if (clauses.size() >= MAX_MERGED_CLAUSES) {
                    return clauses;
                }
                String clause = substantiveClauseOrNull(para);
                if (clause != null) {
                    clauses.add(clause);
                }
            }
            Matcher clauseMatcher = PERF_CLAUSE_PATTERN.matcher(sectionContent);
            while (clauseMatcher.find() && clauses.size() < MAX_MERGED_CLAUSES) {
                String clause = substantiveClauseOrNull(clauseMatcher.group());
                if (clause != null) {
                    clauses.add(clause);
                }
            }
        }
        return clauses;
    }

    private static String substantiveClauseOrNull(String clause) {
        if (!hasText(clause) || !isSubstantivePerformanceRequirement(clause)) {
            return null;
        }
        return clause.trim();
    }

    private static boolean isDuplicate(Set<String> clauses, String clause) {
        for (String existing : clauses) {
            if (existing.contains(clause)) {
                return true;
            }
        }
        return false;
    }

    private static String truncateClause(String clause) {
        return clause.length() > MAX_CLAUSE_CHARS ? clause.substring(0, MAX_CLAUSE_CHARS) : clause;
    }

    static boolean isSubstantivePerformanceRequirement(String text) {
        if (!hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (!lower.contains("业绩") && !lower.contains("供货") && !lower.contains("同类项目")) {
            return false;
        }
        // 必须含有实质门槛指标特征（排除如“经营业绩良好”、“展示公司业绩”等纯描述背景词）
        return lower.contains("年") || lower.contains("项") || lower.contains("万")
                || lower.contains("元") || lower.contains("发票") || lower.contains("不低于")
                || lower.contains("不少于") || lower.contains("累计") || lower.contains("合同")
                || lower.contains("电商") || lower.contains("供应");
    }
}
