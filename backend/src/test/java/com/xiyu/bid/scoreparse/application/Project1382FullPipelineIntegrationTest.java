package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.dto.ScoreParseItemsDTO;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseEvidenceRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseFactRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class Project1382FullPipelineIntegrationTest {

    @Autowired
    private ScoreItemRepository itemRepository;

    @Autowired
    private ScoreResultRepository resultRepository;

    @Autowired
    private ScoreParseFactRepository factRepository;

    @Autowired
    private ScoreParseEvidenceRepository evidenceRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("项目1382全链路验证：Sidecar真实表格产物 + LLM抽取事实 -> 判定AUTO_APPROVED -> 叶子满分100 -> 落库13项 -> UI清单聚合100分")
    void verifyProject1382EndToEndPipeline() throws Exception {
        // 1. 加载 Sidecar 产物（从 classpath 加载真实结构化资产）
        DocumentArtifact artifact;
        try (InputStream is = getClass().getResourceAsStream("/scoreparse/project-1382/project-1382-scoring-tables.json")) {
            assertThat(is).as("Scoring tables resource must exist").isNotNull();
            artifact = objectMapper.readValue(is, DocumentArtifact.class);
        }
        assertThat(artifact.tables()).isNotEmpty();

        // 2. 加载 LLM 抽取事实（从 classpath 加载真实事实快照）
        List<MultimodalScoreFact> rawFacts = new ArrayList<>();
        List<String> lines;
        try (InputStream is = getClass().getResourceAsStream("/scoreparse/project-1382/project-1382-facts.json")) {
            assertThat(is).as("Facts resource must exist").isNotNull();
            lines = new String(is.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
        for (String line : lines) {
            if (line.isBlank()) continue;
            JsonNode node = objectMapper.readTree(line);
            String code = node.path("code").asText("");
            BigDecimal weight = new BigDecimal(node.path("weight").asText("0"));
            String ruleText = node.path("ruleText").asText("");
            String quotedText = node.path("quotedText").asText("");
            String dimension = node.path("dimension").asText("");
            String scoreType = node.path("scoreType").asText("OBJECTIVE");
            String parentCode = node.path("parentCode").asText("");
            Integer page = node.hasNonNull("pageNumber") ? node.path("pageNumber").asInt() : null;
            String tableId = node.path("tableId").asText("");

            String sectionTitle = node.path("sectionTitle").asText("第三章 评审办法");
            String scopeStatus = node.path("scopeStatus").asText("");
            String schemeId = node.path("schemeId").asText("");

            rawFacts.add(new MultimodalScoreFact(
                    sectionTitle, code, parentCode, dimension, weight, ruleText, scoreType,
                    page, tableId, List.of(), quotedText, 0.9, ChildAggregation.SUM, schemeId, scopeStatus,
                    new com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata(ruleText, quotedText, List.of(), List.of(),
                            dimension, scoreType,
                            new com.xiyu.bid.scoreparse.domain.ModelSourceClaim(sectionTitle, page, tableId, List.of(), "", ""))
            ));
        }

        // 3. 运行核心算法：事实证据绑定与结构化校验
        String tenderText = "第三章 评审办法\n3.2.1 分值构成（总分100分）：（1）商务部分：15分；（2）技术部分：45分；（3）报价：40分。";
        OriginalScoringContext context = OriginalScoringContext.resolve(artifact, tenderText);
        var validator = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator();
        var boundFacts = TextPrimaryEvidenceBinder.bind(rawFacts, artifact, tenderText);
        var report = validator.validate(boundFacts, artifact, context, tenderText, new BigDecimal("100"));
        var extractionResult = new MultimodalScoreFactExtractor.ExtractionResult(new BigDecimal("100"), rawFacts, "gemini-3.5-flash", 0);

        StructuredTableResult result = MultimodalStructuredExtractionHandler.run(
                "办公用品.pdf", tenderText, artifact, context, null, () -> {},
                extractionResult, true, false, true);

        // 4. 断言核心算法判定结果
        assertThat(result.completeness()).isNotNull();
        assertThat(result.completeness().leafWeightSum()).isEqualByComparingTo("100");
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.completeness().defects()).isEmpty();
        assertThat(result.candidates()).hasSize(13);

        // 验证关键曾丢失的项恢复
        assertThat(result.candidates()).anySatisfy(c -> {
            assertThat(c.code()).contains("3.2.3(3)_1");
            assertThat(c.weight()).isEqualByComparingTo("28");
        });
        assertThat(result.candidates()).anySatisfy(c -> {
            assertThat(c.code()).contains("3.2.3(3)_2");
            assertThat(c.weight()).isEqualByComparingTo("12");
        });
        assertThat(result.candidates()).anySatisfy(c -> {
            assertThat(c.code()).contains("3.2.3(2)_1");
            assertThat(c.weight()).isEqualByComparingTo("10");
        });
        assertThat(result.candidates()).anySatisfy(c -> {
            assertThat(c.code()).contains("3.2.3(2)_3");
            assertThat(c.weight()).isEqualByComparingTo("8");
        });

        // 5. 走持久化落库（模拟 StructuredTableExtractionHandler 落库阶段）
        ScoreItemPersistenceService persistenceService = new ScoreItemPersistenceService(
                itemRepository, resultRepository, factRepository, evidenceRepository, objectMapper);

        Long projectId = 1382L;
        Long taskId = 284L;
        persistenceService.persistItems(projectId, taskId, result.candidates());

        // 6. 验证数据库中持久化的评分项
        List<ScoreItem> persistedItems = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        assertThat(persistedItems).hasSize(13);
        BigDecimal totalWeightInDb = persistedItems.stream()
                .map(ScoreItem::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalWeightInDb).isEqualByComparingTo("100");

        // 7. 模拟前端 UI 调用的 SummaryAggregator，验证 UI 响应结构
        var summary = new com.xiyu.bid.scoreparse.domain.SummaryAggregator().aggregate(
                persistedItems.stream().map(i -> new com.xiyu.bid.scoreparse.domain.SummaryAggregator.Item(
                        i.getWeight(), i.getScoreType(), i.getEstScore(), i.getStatusStage1())).toList());

        assertThat(summary.totalWeight()).isEqualByComparingTo("100");
        assertThat(summary.weightWarning()).isFalse();
    }
}
