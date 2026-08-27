// Input: scoreparse-golden/ 下合成招标夹具 md + .expected.json 金标标注
// Output: 每案 precision/recall/F1 硬断言 + target/scoreparse-golden-report.json 回放报告
// Pos: scoreparse/golden —— 纯 JUnit5，无 Spring 上下文，静态驱动确定性管线（LLM 层不在范围）
// 维护声明: 维护者按项目SOP；spec 057 US3 / plan D-C

package com.xiyu.bid.scoreparse.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.biddraftagent.infrastructure.openai.ScoringItemExtractor;
import com.xiyu.bid.scoreparse.domain.GhostCandidateFilter;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreItemMergePolicy;
import com.xiyu.bid.scoreparse.domain.ScoreTableRecallLane;
import com.xiyu.bid.scoreparse.domain.WeightNormalizationPolicy;
import com.xiyu.bid.scoreparse.infrastructure.structure.MarkdownScoreSectionLocator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 金标夹具回放基线 v0（spec 057 FR-09 / SC-4）。
 *
 * <p><b>范围声明</b>：本测试只驱动确定性管线六件套
 * {@code MarkdownScoreSectionLocator.locate → ScoringItemExtractor.extract}（正则结构通道）、
 * {@code ScoreTableRecallLane.recall}（表格行直抽通道）、
 * {@code ScoreItemMergePolicy.merge → GhostCandidateFilter.filter}（生产同序），
 * 再复刻 {@code ScoreParseAppService#filterInvalidWeights} 的权重有效性过滤后调用
 * {@code WeightNormalizationPolicy.normalize}。<b>LLM 层不在范围</b>；
 * 本测试即 pre-push 层的夹具回放门禁（waiver 见 spec/057），随默认后端测试套件执行。
 *
 * <p><b>F1 度量口径</b>：在守卫过滤后、权重归一化<b>之前</b>的候选清单上计算（匹配谓词定义唯一出处见
 * scripts/eval-scoreparse/README.md §匹配谓词；{@link #computePrF1} 为同语义参考实现）。
 * 归一化是纯算术投影（等比例缩放会整体改写分值），而 expected 标注是人工真值，
 * 故归一化仅做行为断言（originalTotal 记录与缩放后合计=100），不参与 P/R/F1。
 *
 * <p>每案断言 F1 ≥ expected.threshold（硬断言）；报告写盘失败仅记录不致挂测试。
 */
class GoldenCasesReplayTest {

    private static final String FIXTURE_DIR = "/scoreparse-golden/";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final String LINE_SEPARATOR = System.lineSeparator();
    /** 与 ScoreParseAppService#MAX_SINGLE_WEIGHT 对齐（权重有效上界） */
    private static final BigDecimal MAX_SINGLE_WEIGHT = new BigDecimal("100");

    /** 跨参数化用例收集回放结果，@AfterAll 统一落盘 */
    private static final List<ReplayReportRow> REPORT_ROWS =
            Collections.synchronizedList(new ArrayList<>());

    /** 期望标注条目（expected.json items 元素） */
    record ExpectedItem(String code, String dim, String detail, BigDecimal weight) {
    }

    /** 匹配谓词计算结果：tp/fp/fn 与 P/R/F1 */
    public record Prf(int tp, int fp, int fn, double precision, double recall, double f1) {
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("case-dual-section-renumber"),
                Arguments.of("case-weight-gap-mixed"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void replayGoldenCase(String caseName) throws IOException {
        String markdown = readFixture(caseName + ".md");
        JsonNode expected = MAPPER.readTree(readFixture(caseName + ".expected.json"));
        List<ExpectedItem> expectedItems = readExpectedItems(expected);
        double threshold = expected.path("threshold").asDouble();

        // ── lane 召回（各 lane 原始候选计数先记录，便于分层定位回归漂移在哪一层）──
        List<MarkdownScoreSectionLocator.ScoreSection> sections =
                MarkdownScoreSectionLocator.locate(markdown);
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

        // ── 生产同序守卫链：merge → ghost 过滤 → 权重有效性过滤 ──
        List<ScoreCandidate> merged = new ScoreItemMergePolicy().merge(pool);
        List<ScoreCandidate> ghostFiltered = new GhostCandidateFilter().filter(merged);
        List<ScoreCandidate> curated = filterInvalidWeights(ghostFiltered);

        // ── 权重归一化行为断言（不参与 F1 口径，理由见类 Javadoc）──
        WeightNormalizationPolicy.Result normalized =
                new WeightNormalizationPolicy().normalize(curated);
        BigDecimal preTotal = sumWeights(curated);
        BigDecimal postTotal;
        if (normalized.originalTotal() != null) {
            assertTrue(preTotal.compareTo(normalized.originalTotal()) == 0,
                    () -> caseName + " originalTotal 应等于归一化前合计");
            assertTrue(preTotal.subtract(ONE_HUNDRED).abs()
                            .compareTo(new BigDecimal("0.5")) > 0,
                    () -> caseName + " 合计在容差内却触发了归一化");
            postTotal = sumWeights(normalized.normalizedCandidates());
            assertTrue(postTotal.compareTo(ONE_HUNDRED) == 0,
                    () -> caseName + " 归一化后权重合计必须=100，实际=" + postTotal);
        } else {
            assertTrue(preTotal.subtract(ONE_HUNDRED).abs()
                            .compareTo(new BigDecimal("0.5")) <= 0,
                    () -> caseName + " 合计偏离100却在容差内未触发归一化？preTotal=" + preTotal);
            postTotal = preTotal;
        }

        // ── P/R/F1 硬断言（口径=curated，即归一化前）──
        Prf prf = computePrF1(curated, expectedItems);
        assertTrue(prf.f1() >= threshold,
                () -> caseName + " 金标回放 F1=" + prf.f1() + " < threshold=" + threshold
                        + LINE_SEPARATOR + describeMismatch(curated, expectedItems));

        Map<String, Integer> laneCounts = new LinkedHashMap<>();
        laneCounts.put("regex", regexSectionsRaw);
        laneCounts.put("regexFullText", regexFullTextRaw);
        laneCounts.put("table", tableCandidates.size());
        REPORT_ROWS.add(new ReplayReportRow(caseName, laneCounts, prf, preTotal, postTotal,
                normalized.originalTotal()));
    }

    // ───────────────────────── 匹配谓词（口径唯一实现之一，文字化规范在 eval-scoreparse/README.md） ─────────────────────────

    /**
     * 匹配谓词 + 贪心唯一配对：
     * <ul>
     *   <li>归一化 = trim 后移除全部空白字符（\s+ → 空串）</li>
     *   <li>item 匹配 expected ⇔ detail 归一化互含 且 双方 weight 非空且 |Δweight| ≤ 0.5</li>
     *   <li>按 expected 顺序贪心配对，预测项消费一次后不可复用（防一项吃多个）</li>
     * </ul>
     */
    public static Prf computePrF1(List<ScoreCandidate> predicted, List<ExpectedItem> expectedItems) {
        boolean[] consumed = new boolean[predicted.size()];
        int tp = 0;
        for (ExpectedItem exp : expectedItems) {
            for (int i = 0; i < predicted.size(); i++) {
                if (!consumed[i] && matches(predicted.get(i), exp)) {
                    consumed[i] = true;
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
        return new Prf(tp, fp, fn, precision, recall, f1);
    }

    /** detail 归一化互含 且 weight 差 ≤0.5（weight 任一为空视为不匹配） */
    static boolean matches(ScoreCandidate candidate, ExpectedItem expected) {
        if (candidate.weight() == null || expected.weight() == null) {
            return false;
        }
        if (candidate.weight().subtract(expected.weight()).abs()
                .compareTo(new BigDecimal("0.5")) > 0) {
            return false;
        }
        String predDetail = squash(candidate.detail());
        String expDetail = squash(expected.detail());
        if (expDetail.isEmpty() || predDetail.isEmpty()) {
            return false;
        }
        return predDetail.contains(expDetail) || expDetail.contains(predDetail);
    }

    /** 归一化：trim 并移除全部空白字符 */
    public static String squash(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", "");
    }

    // ───────────────────────── 夹具读取与映射 ─────────────────────────

    private static String readFixture(String fileName) {
        try (var in = GoldenCasesReplayTest.class.getResourceAsStream(FIXTURE_DIR + fileName)) {
            if (in == null) {
                throw new IllegalStateException("金标夹具缺失: src/test/resources" + FIXTURE_DIR + fileName);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取金标夹具失败: " + fileName, e);
        }
    }

    private static List<ExpectedItem> readExpectedItems(JsonNode expectedRoot) {
        List<ExpectedItem> items = new ArrayList<>();
        for (JsonNode node : expectedRoot.path("items")) {
            items.add(new ExpectedItem(
                    node.path("code").asText(""),
                    node.path("dim").asText(""),
                    node.path("detail").asText(),
                    node.hasNonNull("weight") ? new BigDecimal(node.get("weight").asText()) : null));
        }
        return items;
    }

    /**
     * 正则候选 → 域对象映射。与生产 {@code ScoreCandidateMapper#fromCriterion}
     * 同语义（该类包级私有不可跨包复用，此处按序镜像：indicator 优先、source 截断500、semanticPattern 固定标记）。
     */
    // 漂移哨兵/单一真相提示：本文件对候选字段的手工映射是
    // backend/.../scoreparse/infrastructure/openai/ScoreCandidateMapper#fromCriterion 的测试侧镜像
    // （indicator 优先 / source 截断 500）。修改 Mapper 字段序或截断策略时 MUST 同步此处，
    // 否则金标 F1 口径静默漂移。见 spec 057 反 slop 审查 #2。
    private static ScoreCandidate toCandidate(ScoringCriterion criterion,
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

    private static String safeTrim(String text) {
        return text == null ? "" : text.trim();
    }

    private static String truncate(String text) {
        return text == null || text.length() <= 500 ? text : text.substring(0, 500);
    }

    /**
     * 权重有效性过滤：weight 非空且 ∈ (0, 100]。与生产
     * {@code ScoreParseAppService#filterInvalidWeights} 同规则——前附表程序条款等
     * 无分值噪声候选在此落榜（幽灵规则一测的是「得分」形态，两者互补）。
     */
    private static List<ScoreCandidate> filterInvalidWeights(List<ScoreCandidate> candidates) {
        List<ScoreCandidate> valid = new ArrayList<>();
        for (ScoreCandidate candidate : candidates) {
            BigDecimal weight = candidate.weight();
            if (weight != null && weight.compareTo(BigDecimal.ZERO) > 0
                    && weight.compareTo(MAX_SINGLE_WEIGHT) <= 0) {
                valid.add(candidate);
            }
        }
        return valid;
    }

    private static BigDecimal sumWeights(List<ScoreCandidate> candidates) {
        BigDecimal total = BigDecimal.ZERO;
        for (ScoreCandidate candidate : candidates) {
            if (candidate.weight() != null) {
                total = total.add(candidate.weight());
            }
        }
        return total;
    }

    /** 断言失败时的差异清单（未命中期望 + 未配对预测），供修复者一眼定位 */
    private static String describeMismatch(List<ScoreCandidate> predicted,
                                           List<ExpectedItem> expectedItems) {
        StringBuilder sb = new StringBuilder("未命中期望项:");
        boolean anyMissing = false;
        for (ExpectedItem exp : expectedItems) {
            boolean hit = predicted.stream()
                    .anyMatch(pred -> matches(pred, exp));
            if (!hit) {
                anyMissing = true;
                sb.append(LINE_SEPARATOR).append("  - [").append(exp.code()).append("][")
                        .append(exp.dim()).append("] w=").append(exp.weight()).append(' ')
                        .append(head(exp.detail()));
            }
        }
        if (!anyMissing) {
            sb.append(" 无");
        }
        sb.append(LINE_SEPARATOR).append("未配对预测候选:");
        boolean anyExtra = false;
        for (ScoreCandidate pred : predicted) {
            boolean matched = expectedItems.stream()
                    .anyMatch(exp -> matches(pred, exp));
            if (!matched) {
                anyExtra = true;
                sb.append(LINE_SEPARATOR).append("  - [").append(pred.code()).append("][")
                        .append(pred.dim()).append("] w=").append(pred.weight()).append(' ')
                        .append(head(pred.detail())).append(" src=").append(pred.sourceText() == null
                        ? "" : head(pred.sourceText()));
            }
        }
        if (!anyExtra) {
            sb.append(" 无");
        }
        return sb.toString();
    }

    private static String head(String text) {
        String squashed = squash(text);
        return squashed.length() <= 40 ? squashed : squashed.substring(0, 40) + "…";
    }

    /** 报告行中间结构（@AfterAll 统一序列化为 report.json） */
    private record ReplayReportRow(String caseName, Map<String, Integer> laneCounts,
                                   Prf prf, BigDecimal preTotal, BigDecimal postTotal,
                                   BigDecimal originalTotal) {
    }

    // ───────────────────────── 报告落盘（失败仅日志，不拖垮 CI） ─────────────────────────

    @AfterAll
    static void writeReplayReport() {
        Path reportPath = Paths.get(System.getProperty("user.dir"), "target",
                "scoreparse-golden-report.json");
        try {
            StringBuilder json = new StringBuilder("{\"cases\":[");
            synchronized (REPORT_ROWS) {
                for (int i = 0; i < REPORT_ROWS.size(); i++) {
                    ReplayReportRow row = REPORT_ROWS.get(i);
                    if (i > 0) {
                        json.append(',');
                    }
                    appendCaseJson(json, row);
                }
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

    private static void appendCaseJson(StringBuilder json, ReplayReportRow row) {
        json.append("{\"case\":\"").append(row.caseName()).append('"')
                .append(",\"laneCounts\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : row.laneCounts().entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
        }
        json.append('}')
                .append(",\"tp\":").append(row.prf().tp())
                .append(",\"fp\":").append(row.prf().fp())
                .append(",\"fn\":").append(row.prf().fn())
                .append(",\"precision\":").append(row.prf().precision())
                .append(",\"recall\":").append(row.prf().recall())
                .append(",\"f1\":").append(row.prf().f1())
                .append(",\"weightTotalBeforeNormalize\":").append(row.preTotal())
                .append(",\"weightTotalAfterNormalize\":").append(row.postTotal())
                .append(",\"originalTotal\":")
                .append(row.originalTotal() == null ? "null" : row.originalTotal())
                .append('}');
    }

    /** 镜像漂移哨兵：Mapper 口径变更（字段序/截断）若未同步，此固定向量断言会先红 */
    @org.junit.jupiter.api.Test
    void mirrorMatchesMapperContract_fixedVector() {
        var criterion = new com.xiyu.bid.biddraftagent.domain.ScoringCriterion(
                "2-1", "技术", "方案完整可行得10分", new java.math.BigDecimal("10"), null);
        ScoreCandidate c = toCandidate(criterion, null);
        org.junit.jupiter.api.Assertions.assertAll(
                () -> org.junit.jupiter.api.Assertions.assertEquals("2-1", c.code()),
                () -> org.junit.jupiter.api.Assertions.assertEquals("技术", c.dim()),
                () -> org.junit.jupiter.api.Assertions.assertEquals("方案完整可行得10分", c.detail()),
                () -> org.junit.jupiter.api.Assertions.assertEquals(new java.math.BigDecimal("10"), c.weight()));
    }
}
