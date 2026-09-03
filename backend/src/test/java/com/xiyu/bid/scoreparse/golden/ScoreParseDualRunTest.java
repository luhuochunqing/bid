// Input: scoreparse-golden/ 下合成招标夹具 md + .expected.json 金标标注
// Output: 双轨回放比较（Legacy vs Evidence Pipeline）+ target/scoreparse-dual-run-report.json
// Pos: scoreparse/golden —— 纯 JUnit5，无 Spring 上下文（spec 059 Phase 5 双轨回放）
// 维护声明: 维护者按项目SOP；spec 059 Phase 5

package com.xiyu.bid.scoreparse.golden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.GhostCandidateFilter;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreItemMergePolicy;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.ScoreTableRecallLane;
import com.xiyu.bid.scoreparse.domain.WeightNormalizationPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreParseDualRunTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<Map<String, Object>> DUAL_RUN_RECORDS = new ArrayList<>();

    @ParameterizedTest(name = "双轨回放: {0}")
    @ValueSource(strings = {"case-dual-section-renumber", "case-weight-gap-mixed"})
    @DisplayName("双轨回放比较：Legacy vs Evidence Pipeline")
    void testDualRunComparison(String caseName) throws IOException {
        String mdContent = loadFixture(caseName + ".md");

        // 1. 运行 Legacy 链路（确定性召回 + 启发式合并 + 过滤 + 模拟旧静默归一化）
        List<ScoreCandidate> pool = ScoreTableRecallLane.recall(mdContent);
        List<ScoreCandidate> merged = new ScoreItemMergePolicy().merge(pool);
        List<ScoreCandidate> legacyCurated = new GhostCandidateFilter().filter(merged);
        WeightNormalizationPolicy.Result normalized = new WeightNormalizationPolicy().normalize(legacyCurated);

        BigDecimal legacyPreWeight = legacyCurated.stream()
                .map(ScoreCandidate::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal legacyPostWeight = normalized.originalTotal() != null
                ? new BigDecimal("100.00") : legacyPreWeight;
        String legacyDecision = legacyCurated.isEmpty() ? "PARSE_FAILED" : "AUTO_APPROVED";

        // 2. 运行 Evidence 链路（映射为事实池 + 完整性判定 + 拒绝自动归一化）
        List<ScoreFact> evidenceFacts = new ArrayList<>();
        for (int i = 0; i < legacyCurated.size(); i++) {
            ScoreCandidate c = legacyCurated.get(i);
            evidenceFacts.add(new ScoreFact(
                    "fact-" + (i + 1),
                    "tbl-1",
                    List.of("c-" + (i + 1)),
                    c.sourceText() != null ? c.sourceText() : c.detail(),
                    c.code(),
                    c.dim(),
                    c.weight() != null ? c.weight() : BigDecimal.ZERO,
                    null,
                    null,
                    null,
                    "TABLE_ROW",
                    0.95,
                    ScoreFact.ValidationStatus.AUTO_APPROVED
            ));
        }

        ScoreCompletenessPolicy.ScoreCompletenessResult completeness =
                ScoreCompletenessPolicy.evaluateCompleteness(evidenceFacts, mdContent, true);

        // 3. 记录差异
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("case", caseName);
        record.put("legacyItemCount", legacyCurated.size());
        record.put("legacyPreWeight", legacyPreWeight);
        record.put("legacyPostWeight", legacyPostWeight);
        record.put("legacyDecision", legacyDecision);
        record.put("evidenceFactCount", evidenceFacts.size());
        record.put("evidenceTotalWeight", completeness.leafWeightSum());
        record.put("evidenceDecision", completeness.decision().name());
        record.put("evidenceDefects", completeness.defects());

        // 断言零 P0 缺陷：Evidence Pipeline 绝不允许静默归一化改写分值
        if (legacyPreWeight.compareTo(new BigDecimal("100")) != 0) {
            // 在权重偏离 100 时，旧链路通过归一化强行变成 100 并 AUTO_APPROVED；新链路必须打标 REVIEW_REQUIRED 阻断
            assertThat(completeness.decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
            assertThat(completeness.defects()).isNotEmpty();
        }

        DUAL_RUN_RECORDS.add(record);
    }

    @AfterAll
    static void writeDualRunReport() {
        if (DUAL_RUN_RECORDS.isEmpty()) {
            return;
        }
        try {
            Path targetDir = Paths.get("target");
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            Path reportPath = targetDir.resolve("scoreparse-dual-run-report.json");
            Files.writeString(reportPath, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(DUAL_RUN_RECORDS));
        } catch (IOException e) {
            System.err.println("Failed to write dual run report: " + e.getMessage());
        }
    }

    private String loadFixture(String fileName) throws IOException {
        var is = getClass().getResourceAsStream("/scoreparse-golden/" + fileName);
        if (is == null) {
            throw new IllegalArgumentException("Fixture not found: " + fileName);
        }
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
}
