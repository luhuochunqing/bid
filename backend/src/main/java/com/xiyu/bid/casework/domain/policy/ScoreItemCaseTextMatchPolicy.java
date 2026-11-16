package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.cleanTitle;
import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.extractTokens;
import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.hasText;

/**
 * 纯核心：评分项与案例文本的语料级 IDF/BM25 条款重叠及标题跨层相似度计算策略。
 *
 * <p>从 {@link ScoreItemCaseMatchPolicy} 拆出以保持单文件职责内聚与行数预算可控。</p>
 */
final class ScoreItemCaseTextMatchPolicy {

    private static final int MAX_DETAIL_SIMILARITY_SCORE = 25;
    private static final int MAX_TITLE_SIMILARITY_SCORE = 20;
    private static final double BM25_K1 = 1.2;
    private static final double BM25_B = 0.75;

    /** 评分细则档位（优秀得8-10分/一般得4-7分…）是模板噪声，与评分项业务主题无关。 */
    private static final Pattern RUBRIC_BAND_PATTERN = Pattern.compile(
            "(?:优秀|良好|较好|一般|较差|差)的?得\\s*[0-9.]+(?:\\s*[-~～至]\\s*[0-9.]+)?\\s*分");
    /** 仅剥离可识别的分类（可选）、数字序号、标题及前置分值，保留正文及其后的单元格。 */
    private static final Pattern TABLE_ROW_PREFIX_PATTERN = Pattern.compile(
            "^(?:\\|\\h*)?(?:[^|\\r\\n]+\\|\\h*)?\\d+(?:\\.\\d+)*\\h*\\|\\h*"
                    + "[^|\\r\\n]+\\|\\h*(?:\\d+(?:\\.\\d+)?\\h*\\|\\h*)?");

    private ScoreItemCaseTextMatchPolicy() {}

    /**
     * 候选案例语料统计：条款词 IDF 权重与平均文本长度（BM25 长度归一化）。
     * 空语料表示单条或极少候选评估，等权且不做长度归一。
     */
    record Corpus(Map<String, Double> idf, double averageLength) {
        static final Corpus EMPTY = new Corpus(Map.of(), 0);

        double weight(String token) {
            return idf.isEmpty() ? 1.0 : idf.getOrDefault(token, 0.0);
        }

        /** BM25 二值词频下的长度因子：长文本凭篇幅"顺带"包含条款词的贡献被压低。 */
        double lengthFactor(int length) {
            if (averageLength <= 0) return 1.0;
            return (BM25_K1 + 1) / (1 + BM25_K1 * (1 - BM25_B + BM25_B * length / averageLength));
        }
    }

    static Corpus buildCorpus(
            String detail, String qualificationRequirementText, List<? extends KnowledgeCaseReadModel> candidates) {
        List<String> texts = candidates.stream().filter(Objects::nonNull)
                .map(ScoreItemCaseTextMatchPolicy::candidateFullText).toList();
        // 语料过小（如单条测试桩或极少候选）不足以支撑统计 IDF 与平均长度，退化为等权评估
        if (texts.size() < 5) return Corpus.EMPTY;
        Set<String> vocabulary = new LinkedHashSet<>(requirementTokens(detail));
        vocabulary.addAll(requirementTokens(qualificationRequirementText));
        Map<String, Double> idf = new HashMap<>();
        for (String token : vocabulary) {
            long df = texts.stream().filter(text -> text.contains(token)).count();
            // 只保留语料中出现过的词；出现在全部案例中的词 IDF=0，不具区分度
            if (df > 0) idf.put(token, Math.log((texts.size() + 1.0) / (df + 1.0)));
        }
        double averageLength = texts.stream().mapToInt(String::length).average().orElse(0);
        return new Corpus(idf, averageLength);
    }

    /**
     * 条款重叠度：按词权重计算评分项条款词在案例中的覆盖率。
     * 无语料权重时（单条评估）退化为等权覆盖率；不再按命中个数保底，避免长文本凭泛词命中数刷满分。
     */
    static int calculateDetailOverlap(String detail, String candidateText, Corpus corpus) {
        if (!hasText(detail) || !hasText(candidateText)) return 0;
        double total = 0;
        double hit = 0;
        for (String token : requirementTokens(detail)) {
            double weight = corpus.weight(token);
            if (weight <= 0) continue;
            total += weight;
            if (candidateText.contains(token)) hit += weight;
        }
        if (hit <= 0) return 0;
        double coverage = hit / total * corpus.lengthFactor(candidateText.length());
        int score = (int) Math.round(coverage * 3.0 * MAX_DETAIL_SIMILARITY_SCORE);
        return Math.max(1, Math.min(MAX_DETAIL_SIMILARITY_SCORE, score));
    }

    /** 评分项条款中参与重叠计算的词：剥离表格前缀与评分档位模板后分词，去停用词，统一小写。 */
    static Set<String> requirementTokens(String requirement) {
        if (!hasText(requirement)) return Set.of();
        String body = TABLE_ROW_PREFIX_PATTERN.matcher(requirement.trim()).replaceFirst("");
        body = RUBRIC_BAND_PATTERN.matcher(body).replaceAll(" ");
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : extractTokens(body)) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (lower.length() >= 2 && !TextSimilarityPolicy.STOP_WORDS.contains(lower)) tokens.add(lower);
        }
        return tokens;
    }

    static int calculateTitleSimilarity(
            ScoreItemCaseMatchPolicy.ScoreItemContext context,
            KnowledgeCaseReadModel candidate,
            String candidateText) {
        String candidateTitle = candidate.getScoringPointTitle();
        String candText = candidateText != null ? candidateText : "";
        String cleanDim = cleanTitle(context.dim());
        String cleanCode = cleanTitle(context.code());

        if (!hasText(cleanDim) && !hasText(context.dim())) {
            if (hasText(context.projectName()) && hasText(candidate.getSourceProjectName())) {
                double jaccard = TextSimilarityPolicy.jaccardSimilarityOfText(
                        context.projectName(), candidate.getSourceProjectName());
                return (int) Math.round(jaccard * MAX_TITLE_SIMILARITY_SCORE);
            }
            return 0;
        }

        if (hasText(cleanDim) && cleanDim.length() >= 2 && hasText(candidateTitle)
                && (candidateTitle.equalsIgnoreCase(cleanDim)
                || candidateTitle.contains(cleanDim) || cleanDim.contains(candidateTitle))) {
            return MAX_TITLE_SIMILARITY_SCORE;
        }

        if (hasText(candidateTitle)) {
            String lowerCandTitle = candidateTitle.trim().toLowerCase(Locale.ROOT);
            if ((hasText(context.detail()) && context.detail().toLowerCase(Locale.ROOT).contains(lowerCandTitle))
                    || (hasText(context.dim()) && context.dim().toLowerCase(Locale.ROOT).contains(lowerCandTitle))) {
                return MAX_TITLE_SIMILARITY_SCORE;
            }
        }

        // 案例标题（案例"讲的是什么"）被评分项条款覆盖的比例：标题主题落在条款要求内才算评分点匹配
        int score = (int) Math.round(titleCoverage(candidateTitle, context.dim(), context.detail()) * MAX_TITLE_SIMILARITY_SCORE);

        String queryTitle = String.join(" ",
                Stream.of(cleanDim, cleanCode, context.keyword()).filter(TextSimilarityPolicy::hasText).toList());
        if (!hasText(queryTitle)) return score;

        Set<String> queryTokens = extractTokens(queryTitle);
        Set<String> targetTokens = extractTokens(candidateTitle);
        if (queryTokens.isEmpty()) return score;

        double jaccard = TextSimilarityPolicy.jaccardSimilarity(queryTokens, targetTokens);
        score = Math.max(score, (int) Math.round(jaccard * MAX_TITLE_SIMILARITY_SCORE));
        // 停用词（管理/方案/服务…）不计入正文覆盖，避免任何长文本都"部分匹配"评分点
        List<String> meaningful = queryTokens.stream()
                .filter(t -> t.length() >= 2)
                .map(t -> t.toLowerCase(Locale.ROOT))
                .filter(t -> !TextSimilarityPolicy.STOP_WORDS.contains(t))
                .toList();
        long hitsInCandidate = meaningful.stream().filter(candText::contains).count();
        if (hitsInCandidate > 0) {
            double textCoverage = (double) hitsInCandidate / meaningful.size();
            score = Math.max(score, (int) Math.round(textCoverage * (MAX_TITLE_SIMILARITY_SCORE - 2)));
        }
        return score;
    }

    private static double titleCoverage(String candidateTitle, String dim, String detail) {
        Set<String> titleTokens = requirementTokens(candidateTitle);
        if (titleTokens.isEmpty()) return 0;
        String requirement = (Objects.toString(dim, "") + " " + Objects.toString(detail, ""))
                .toLowerCase(Locale.ROOT);
        long hits = titleTokens.stream().filter(requirement::contains).count();
        return (double) hits / titleTokens.size();
    }

    static String candidateFullText(KnowledgeCaseReadModel candidate) {
        return (Objects.toString(candidate.getRequirementRaw(), "") + " "
                + Objects.toString(candidate.getResponseText(), "") + " "
                + Objects.toString(candidate.getScoringPointTitle(), "")).toLowerCase(Locale.ROOT);
    }
}
