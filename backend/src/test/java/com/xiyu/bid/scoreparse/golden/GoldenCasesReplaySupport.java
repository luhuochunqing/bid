// Input: scoreparse-golden 回放测试支撑函数与结构（规范口径固定）
// Pos: scoreparse/golden — 在 test-only 范围提炼 P/R/F1 口径与夹具转换，避免主测试类膨胀。
package com.xiyu.bid.scoreparse.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.biddraftagent.infrastructure.openai.ScoringItemExtractor;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreParseConstants;
import com.xiyu.bid.scoreparse.domain.ScoreTableRecallLane;
import com.xiyu.bid.scoreparse.domain.WeightNormalizationPolicy;
import com.xiyu.bid.scoreparse.infrastructure.structure.MarkdownScoreSectionLocator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class GoldenCasesReplaySupport {

    private static final String FIXTURE_DIR = "/scoreparse-golden/";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal NORMALIZATION_TOLERANCE = new BigDecimal("0.5");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private GoldenCasesReplaySupport() {
    }

    record ExpectedItem(String code, String dim, String detail, BigDecimal weight, String sourceText) {
    }

    /** 匹配谓词 + 贪心唯一配对（expected 顺序消费、预测项不可复用） */
    public record Prf(int tp, int fp, int fn, double precision, double recall, double f1) {
    }

    record MatchPlan(Prf prf, boolean[] predictedMatched, boolean[] expectedMatched) {
    }

    /** 回放报告行（报告落盘使用） */
    record ReplayReportRow(String caseName, Map<String, Integer> laneCounts,
                           Prf prf, BigDecimal preTotal, BigDecimal postTotal,
                           BigDecimal originalTotal, String decisionStatus,
                           boolean structurallyValid, double evidenceCoverage) {
    }

    record CandidatePool(List<ScoreCandidate> allCandidates,
                         int regexSectionsRaw,
                         int regexFullTextRaw,
                         List<ScoreCandidate> tableCandidates) {
    }

    static String readFixture(String fileName) {
        try (var in = GoldenCasesReplaySupport.class.getResourceAsStream(FIXTURE_DIR + fileName)) {
            if (in == null) {
                throw new IllegalStateException("金标夹具缺失: src/test/resources" + FIXTURE_DIR + fileName);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取金标夹具失败: " + fileName, e);
        }
    }

    static List<ExpectedItem> readExpectedItems(JsonNode expectedRoot) {
        List<ExpectedItem> items = new ArrayList<>();
        for (JsonNode node : expectedRoot.path("items")) {
            items.add(new ExpectedItem(
                    node.path("code").asText(""),
                    node.path("dim").asText(""),
                    node.path("detail").asText(),
                    node.hasNonNull("weight") ? new BigDecimal(node.get("weight").asText()) : null,
                    node.path("sourceText").asText("")));
        }
        return items;
    }

    static CandidatePool collectCandidates(String markdown) {
        List<MarkdownScoreSectionLocator.ScoreSection> sections = MarkdownScoreSectionLocator.locate(markdown);
        List<ScoreCandidate> pool = new ArrayList<>();

        int regexFullTextRaw = 0;
        for (ScoringCriterion criterion : ScoringItemExtractor.extract(markdown)) {
            pool.add(toCandidate(criterion, null));
            regexFullTextRaw++;
        }

        int regexSectionsRaw = 0;
        for (MarkdownScoreSectionLocator.ScoreSection section : sections) {
            for (ScoringCriterion criterion : ScoringItemExtractor.extract(section.content())) {
                pool.add(toCandidate(criterion, section));
                regexSectionsRaw++;
            }
        }

        List<ScoreCandidate> tableCandidates = ScoreTableRecallLane.recall(markdown);
        pool.addAll(tableCandidates);

        return new CandidatePool(pool, regexSectionsRaw, regexFullTextRaw, tableCandidates);
    }

    static List<ScoreCandidate> filterInvalidWeights(List<ScoreCandidate> candidates) {
        List<ScoreCandidate> valid = new ArrayList<>();
        for (ScoreCandidate candidate : candidates) {
            BigDecimal weight = candidate.weight();
            if (weight != null && weight.compareTo(ZERO) > 0
                    && weight.compareTo(ScoreParseConstants.MAX_SINGLE_WEIGHT) <= 0) {
                valid.add(candidate);
            }
        }
        return valid;
    }

    static MatchPlan matchPredictions(List<ScoreCandidate> predicted, List<ExpectedItem> expectedItems) {
        boolean[] predictedMatched = new boolean[predicted.size()];
        boolean[] expectedMatched = new boolean[expectedItems.size()];
        int tp = 0;

        for (int e = 0; e < expectedItems.size(); e++) {
            ExpectedItem exp = expectedItems.get(e);
            for (int i = 0; i < predicted.size(); i++) {
                if (!predictedMatched[i] && matches(predicted.get(i), exp)) {
                    predictedMatched[i] = true;
                    expectedMatched[e] = true;
                    tp++;
                    break;
                }
            }
        }

        int fn = expectedItems.size() - tp;
        int fp = predicted.size() - tp;
        double precision = tp + fp == 0 ? 0.0 : (double) tp / (tp + fp);
        double recall = tp + fn == 0 ? 0.0 : (double) tp / (tp + fn);
        double f1 = precision + recall == 0 ? 0.0 : 2 * precision * recall / (precision + recall);

        return new MatchPlan(new Prf(tp, fp, fn, precision, recall, f1),
                predictedMatched, expectedMatched);
    }

    static void assertOptionalContracts(String caseName,
                                        JsonNode expected,
                                        List<ExpectedItem> expectedItems,
                                        List<ScoreCandidate> predicted,
                                        BigDecimal preTotal) {
        if (expected.hasNonNull("expectTotal")) {
            BigDecimal expectTotal = new BigDecimal(expected.get("expectTotal").asText());
            assertTrue(preTotal.compareTo(expectTotal) == 0,
                    () -> caseName + " 权重未收敛：归一化前合计=" + preTotal + "，期望=" + expectTotal);
        }

        boolean strictDim = expected.path("strictDim").asBoolean(false);
        boolean needsPairing = strictDim
                || expectedItems.stream().anyMatch(e -> e.sourceText() != null && !e.sourceText().isBlank());
        if (!needsPairing) {
            return;
        }

        for (int[] pair : pairGreedy(predicted, expectedItems)) {
            ExpectedItem exp = expectedItems.get(pair[0]);
            ScoreCandidate pred = predicted.get(pair[1]);
            if (strictDim && !exp.dim().isBlank()) {
                assertTrue(collapseWhitespace(pred.dim()).equals(exp.dim()),
                        () -> caseName + " 维度漂移：期望=[" + exp.dim() + "] 实际=["
                                + pred.dim() + "] 明细=" + head(exp.detail()));
            }
            if (exp.sourceText() != null && !exp.sourceText().isBlank()) {
                assertTrue(pred.sourceText() != null
                                && squash(pred.sourceText()).contains(squash(exp.sourceText())),
                        () -> caseName + " 原文背书缺失：期望锚点=[" + exp.sourceText() + "] 明细="
                                + head(exp.detail()));
            }
        }
    }

    static boolean matches(ScoreCandidate candidate, ExpectedItem expected) {
        if (candidate.weight() == null || expected.weight() == null) {
            return false;
        }
        if (candidate.weight().subtract(expected.weight()).abs().compareTo(NORMALIZATION_TOLERANCE) > 0) {
            return false;
        }
        String predDetail = squash(candidate.detail());
        String expDetail = squash(expected.detail());
        return !expDetail.isEmpty() && !predDetail.isEmpty()
                && (predDetail.contains(expDetail) || expDetail.contains(predDetail));
    }

    static String squash(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", "");
    }

    static String collapseWhitespace(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }

    static List<int[]> pairGreedy(List<ScoreCandidate> predicted, List<ExpectedItem> expectedItems) {
        boolean[] consumed = new boolean[predicted.size()];
        List<int[]> pairs = new ArrayList<>();
        for (int e = 0; e < expectedItems.size(); e++) {
            ExpectedItem exp = expectedItems.get(e);
            for (int i = 0; i < predicted.size(); i++) {
                if (!consumed[i] && matches(predicted.get(i), exp)) {
                    consumed[i] = true;
                    pairs.add(new int[]{e, i});
                    break;
                }
            }
        }
        return pairs;
    }

    static BigDecimal sumWeights(List<ScoreCandidate> candidates) {
        BigDecimal total = BigDecimal.ZERO;
        for (ScoreCandidate candidate : candidates) {
            if (candidate.weight() != null) {
                total = total.add(candidate.weight());
            }
        }
        return total;
    }

    static ScoreCandidate toCandidate(ScoringCriterion criterion,
                                     MarkdownScoreSectionLocator.ScoreSection section) {
        String detail = criterion.indicator() != null && !criterion.indicator().isBlank()
                ? criterion.indicator() : criterion.dimension();
        boolean hasSection = section != null;
        return new ScoreCandidate(
                safeTrim(criterion.itemNumber()),
                safeTrim(criterion.dimension()),
                detail,
                criterion.weight(),
                null,
                hasSection ? section.sectionTitle() : "",
                hasSection ? truncate(section.content()) : "",
                hasSection ? section.location() : "",
                "RECALL_REGEX_STRUCTURE"
        );
    }

    static String describeMismatch(List<ScoreCandidate> predicted, List<ExpectedItem> expectedItems,
                                  boolean[] predictedMatched, boolean[] expectedMatched) {
        StringBuilder sb = new StringBuilder("未命中期望项:");
        boolean anyMissing = false;
        for (int i = 0; i < expectedItems.size(); i++) {
            if (!expectedMatched[i]) {
                anyMissing = true;
                ExpectedItem exp = expectedItems.get(i);
                sb.append(System.lineSeparator()).append("  - [")
                        .append(exp.code()).append("][")
                        .append(exp.dim()).append("] w=")
                        .append(exp.weight()).append(' ')
                        .append(head(exp.detail()));
            }
        }
        if (!anyMissing) {
            sb.append(" 无");
        }

        sb.append(System.lineSeparator()).append("未配对预测候选:");
        boolean anyExtra = false;
        for (int i = 0; i < predicted.size(); i++) {
            if (!predictedMatched[i]) {
                anyExtra = true;
                ScoreCandidate pred = predicted.get(i);
                sb.append(System.lineSeparator()).append("  - [")
                        .append(pred.code()).append("][")
                        .append(pred.dim()).append("] w=")
                        .append(pred.weight()).append(' ')
                        .append(head(pred.detail())).append(" src=")
                        .append(pred.sourceText() == null ? "" : head(pred.sourceText()));
            }
        }
        if (!anyExtra) {
            sb.append(" 无");
        }
        return sb.toString();
    }

    static Map<String, Integer> laneCounts(CandidatePool pool) {
        Map<String, Integer> laneCounts = new LinkedHashMap<>();
        laneCounts.put("regex", pool.regexSectionsRaw());
        laneCounts.put("regexFullText", pool.regexFullTextRaw());
        laneCounts.put("table", pool.tableCandidates().size());
        return laneCounts;
    }

    static double evidenceCoverage(CandidatePool pool) {
        int totalCandidates = pool.tableCandidates().size() + pool.regexSectionsRaw();
        return totalCandidates == 0 ? 0.0
                : (double) pool.tableCandidates().size() / totalCandidates;
    }

    static void appendCaseJson(StringBuilder json, ReplayReportRow row) {
        json.append('{')
                .append("\"case\":\"").append(row.caseName()).append('"')
                .append(",\"laneCounts\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : row.laneCounts().entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey()).append("\":")
                    .append(entry.getValue());
        }
        json.append('}')
                .append(",\"tp\":").append(row.prf().tp())
                .append(",\"fp\":").append(row.prf().fp())
                .append(",\"fn\":").append(row.prf().fn())
                .append(",\"precision\":").append(row.prf().precision())
                .append(",\"recall\":").append(row.prf().recall())
                .append(",\"f1\":").append(row.prf().f1())
                .append(",\"weightTotalBeforeNormalize\":")
                .append(row.preTotal())
                .append(",\"weightTotalAfterNormalize\":")
                .append(row.postTotal())
                .append(",\"originalTotal\":")
                .append(row.originalTotal() == null ? "null" : row.originalTotal())
                .append(",\"decisionStatus\":\"")
                .append(row.decisionStatus()).append('"')
                .append(",\"structurallyValid\":").append(row.structurallyValid())
                .append(",\"evidenceCoverage\":").append(row.evidenceCoverage())
                .append('}');
    }

    static void writeReplayReport(Path reportPath, List<ReplayReportRow> rows) {
        try {
            StringBuilder json = new StringBuilder('{');
            json.append("\"cases\":[");
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                appendCaseJson(json, rows.get(i));
            }
            json.append("]}");
            Files.createDirectories(reportPath.getParent());
            Files.writeString(reportPath, json.toString(), StandardCharsets.UTF_8);
            System.out.println("金标回放报告已写入: " + reportPath.toAbsolutePath());
        } catch (IOException reportFailure) {
            System.err.println("[WARN] 金标回放报告写盘失败（不影响测试结论，但 CI 报告缺档）: "
                    + reportPath.toAbsolutePath() + " reason=" + reportFailure.getMessage());
        }
    }

    private static String head(String text) {
        String squashed = squash(text);
        return squashed.length() <= 40 ? squashed : squashed.substring(0, 40) + "…";
    }

    private static String safeTrim(String text) {
        return text == null ? "" : text.trim();
    }

    private static String truncate(String text) {
        return text == null || text.length() <= 500 ? text : text.substring(0, 500);
    }

    /** 简化入口，避免 tests 在此处直接依赖 Stream 接口做无意义变换 */
    static JsonNode parseExpected(String fixtureContent) throws IOException {
        return MAPPER.readTree(fixtureContent);
    }

    static BigDecimal expectedOneHundred() {
        return ONE_HUNDRED;
    }

    static WeightNormalizationPolicy.Result normalizeCandidates(List<ScoreCandidate> candidates) {
        return new WeightNormalizationPolicy().normalize(candidates);
    }
}
