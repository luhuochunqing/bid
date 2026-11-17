// Input: scoreparse-golden/case-production-cross-chapter-perf 夹具
// Output: 正式跨章节规则回放 + 简单预计分数量比例回放
// Pos: scoreparse/golden
// 维护声明: spec 080 黄金回归用例

package com.xiyu.bid.scoreparse.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.application.EstimatedScoreService;
import com.xiyu.bid.scoreparse.application.match.BrandMatchService;
import com.xiyu.bid.scoreparse.application.match.CertMatchService;
import com.xiyu.bid.scoreparse.application.match.PersonMatchService;
import com.xiyu.bid.scoreparse.application.match.ProjectMatchService;
import com.xiyu.bid.scoreparse.application.match.WarehouseMatchService;
import com.xiyu.bid.scoreparse.domain.IncrementalCountScorePolicy;
import com.xiyu.bid.scoreparse.domain.ScoreItemConstraintExtractor;
import com.xiyu.bid.scoreparse.domain.ScoreTypeClassificationPolicy;
import com.xiyu.bid.scoreparse.domain.policy.CrossChapterPerformancePolicy;
import com.xiyu.bid.scoreparse.dto.ProjectMatchedItem;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionCrossChapterPerformanceGoldenReplayTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String fixtureMarkdown;
    private JsonNode expectedJson;

    @BeforeEach
    void setUp() throws IOException {
        ClassLoader cl = getClass().getClassLoader();
        try (InputStream is = cl.getResourceAsStream("scoreparse-golden/case-production-cross-chapter-perf.md")) {
            assertThat(is).as("case-production-cross-chapter-perf.md fixture exists").isNotNull();
            fixtureMarkdown = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (InputStream is = cl.getResourceAsStream("scoreparse-golden/case-production-cross-chapter-perf.expected.json")) {
            assertThat(is).as("case-production-cross-chapter-perf.expected.json fixture exists").isNotNull();
            expectedJson = MAPPER.readTree(is);
        }
    }

    @Test
    @DisplayName("黄金样本：跨章节业绩门槛提取（品类、金额、凭证）与预期完全对齐")
    void crossChapterExtraction_matchesExpectedFixture() {
        CrossChapterPerformancePolicy policy = new CrossChapterPerformancePolicy();
        JsonNode scoreItemNode = expectedJson.path("scoreItem");
        String detail = scoreItemNode.path("detail").asText();

        CrossChapterPerformancePolicy.Constraint constraint = policy.extract("供应商业绩", detail, List.of(), fixtureMarkdown);

        assertThat(constraint.referencesQualification()).isTrue();
        assertThat(constraint.hasEffectiveConstraint()).isTrue();

        JsonNode expectedQual = expectedJson.path("qualificationRequirement");
        assertThat(constraint.targetCategories()).contains(expectedQual.path("category").asText());
        assertThat(constraint.minContractAmount()).isEqualByComparingTo(expectedQual.path("minAmount").asText());
        assertThat(constraint.evidenceRequirements()).contains("发票");
    }

    @Test
    @DisplayName("黄金样本：评分项主客观分类与递增规则解析对齐")
    void scoreClassificationAndRuleParsing_matchesExpectedFixture() {
        JsonNode scoreItemNode = expectedJson.path("scoreItem");
        String detail = scoreItemNode.path("detail").asText();
        BigDecimal weight = new BigDecimal(scoreItemNode.path("weight").asText());

        // 1. 分类判定必须为客观项
        String scoreType = new ScoreTypeClassificationPolicy().classify(detail, "SUBJECTIVE");
        assertThat(scoreType).isEqualTo(scoreItemNode.path("expectedScoreType").asText());

        // 2. 阶梯计分规则解析
        Optional<IncrementalCountScorePolicy.Rule> ruleOpt = IncrementalCountScorePolicy.parse(detail);
        assertThat(ruleOpt).isPresent();
        IncrementalCountScorePolicy.Rule rule = ruleOpt.get();

        assertThat(rule.baseScore()).isEqualByComparingTo(scoreItemNode.path("baseScore").asText());
        assertThat(rule.pointsPerIncrement()).isEqualByComparingTo(scoreItemNode.path("incrementScore").asText());
        assertThat(rule.maximumScore()).isEqualByComparingTo(scoreItemNode.path("maxScore").asText());
        assertThat(rule.requiredCountForMaximumScore()).isEqualTo(scoreItemNode.path("requiredCountForMax").asInt());

        // 3. 满分所需数量提取
        assertThat(ScoreItemConstraintExtractor.extractProjectRequiredCount(detail, weight))
                .isEqualTo(scoreItemNode.path("requiredCountForMax").asInt());
    }

    @ParameterizedTest(name = "命中业绩数量 {0} 篇 -> 预计折算分 {1}")
    @CsvSource({
            "0, 0.00",
            "1, 1.67",
            "2, 3.33",
            "3, 5.00",
            "4, 5.00"
    })
    @DisplayName("黄金样本：端到端预计得分回放（0~4份业绩计分折算）")
    void endToEndEstimatedScoreReplay(int matchCount, String expectedScore) {
        ScoreItemRepository itemRepository = mock(ScoreItemRepository.class);
        CertMatchService certMatchService = mock(CertMatchService.class);
        PersonMatchService personMatchService = mock(PersonMatchService.class);
        ProjectMatchService projectMatchService = mock(ProjectMatchService.class);
        WarehouseMatchService warehouseMatchService = mock(WarehouseMatchService.class);
        BrandMatchService brandMatchService = mock(BrandMatchService.class);

        EstimatedScoreService customService = new EstimatedScoreService(itemRepository,
                certMatchService, personMatchService, projectMatchService,
                warehouseMatchService, brandMatchService);

        JsonNode scoreItemNode = expectedJson.path("scoreItem");
        String detail = scoreItemNode.path("detail").asText();
        ScoreItem item = ScoreItem.builder()
                .projectId(1L)
                .parseTaskId(1L)
                .itemIndex(1)
                .code("A1")
                .dim("供应商业绩")
                .detail(detail)
                .weight(new BigDecimal(scoreItemNode.path("weight").asText()))
                .scoreType("OBJECTIVE")
                .statusStage1("PENDING")
                .build();

        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(item));

        List<ProjectMatchedItem> matchedItems = new ArrayList<>();
        for (int i = 1; i <= matchCount; i++) {
            matchedItems.add(new ProjectMatchedItem(
                    (long) i,
                    "工业品合同" + i,
                    "工业品",
                    "集团" + i,
                    "央企",
                    LocalDate.of(2023, 1, 1),
                    new BigDecimal("12000000")
            ));
        }

        when(projectMatchService.findEstimateCandidates()).thenReturn(matchedItems);

        customService.estimateForProject(1L);

        if (matchCount == 0) {
            assertThat(item.getEstScore()).isEqualByComparingTo("0.00");
            assertThat(item.getStatusStage1()).isEqualTo("PENDING");
            assertThat(item.getKbHit()).isFalse();
        } else {
            assertThat(item.getEstScore()).isEqualByComparingTo(expectedScore);
            assertThat(item.getKbHit()).isTrue();
            // The first-quotation deadline and qualification conditions are not available to the rough estimate.
            assertThat(item.getStatusStage1()).isEqualTo("PENDING");
            assertThat(item.getEstBasis()).contains("知识库粗估", "未核验");
            assertThat(item.getEstBasis()).contains("工业品");
            assertThat(item.getEstBasis()).contains("要求3项");
        }
    }
}
