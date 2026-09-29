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

    @Test
    @DisplayName("项目1382线上真实Task285事实：双表拓扑(p33大纲表+p34明细表) -> 判定AUTO_APPROVED -> 落库13项100分")
    void verifyTask285OnlineFactsPipeline() throws Exception {
        DocumentArtifact artifact;
        try (InputStream is = getClass().getResourceAsStream("/scoreparse/project-1382/project-1382-scoring-tables.json")) {
            assertThat(is).as("Scoring tables resource must exist").isNotNull();
            artifact = objectMapper.readValue(is, DocumentArtifact.class);
        }
        artifact = SourceArtifactBundle.combine(List.of(artifact));

        JsonNode factsArray;
        try (InputStream is = getClass().getResourceAsStream("/scoreparse/project-1382/task-285-facts.json")) {
            assertThat(is).as("Task 285 facts resource must exist").isNotNull();
            factsArray = objectMapper.readTree(is);
        }

        java.util.Map<String, String> codeByFactId = new java.util.HashMap<>();
        for (JsonNode node : factsArray) {
            codeByFactId.put(node.path("factId").asText(""), node.path("code").asText(""));
        }

        java.util.Map<String, DocumentArtifact.CanonicalTable.CanonicalCellDto> cellMap = new java.util.HashMap<>();
        for (var t : artifact.tables()) {
            for (var c : t.cells()) {
                cellMap.put(c.cellId(), c);
            }
        }

        List<MultimodalScoreFact> rawFacts = new ArrayList<>();
        for (JsonNode node : factsArray) {
            String tableId = node.path("sourceTableId").asText("");
            if (tableId.isBlank()) {
                continue; // 过滤掉合同履约考核等非评分表格事实
            }

            String code = node.path("code").asText("");
            BigDecimal weight = new BigDecimal(node.path("weight").asText("0"));
            String ruleText = node.path("sourceText").asText("");
            String dimension = node.path("dimension").asText("");
            String scoreType = "OBJECTIVE";
            String parentFactId = node.path("parentFactId").asText("");
            String parentCode = parentFactId.isBlank() ? "" : codeByFactId.getOrDefault(parentFactId, "");

            List<String> sourceCellIds = new ArrayList<>();
            for (JsonNode cellNode : node.path("sourceCellIds")) {
                sourceCellIds.add(cellNode.asText());
            }

            Integer page = null;
            String quotedText = "";
            if (!sourceCellIds.isEmpty()) {
                var cell = cellMap.get(sourceCellIds.get(0));
                if (cell != null) {
                    page = cell.sourcePage();
                    if ("business_score".equals(code)) {
                        quotedText = "（1）商务部分：15分";
                    } else if ("technical_score".equals(code)) {
                        quotedText = "（2）技术部分：45分";
                    } else if ("price_score".equals(code)) {
                        quotedText = "（3）报价：40分";
                    } else if ("price_level1".equals(code)) {
                        quotedText = "1、一级集采商品上架折扣率（28分）";
                    } else if ("price_non_level1".equals(code)) {
                        quotedText = "1、非一级集采商品上架折扣率（12分）";
                    } else {
                        quotedText = cell.rawText();
                    }
                }
            }

            rawFacts.add(new MultimodalScoreFact(
                    "第三章 评审办法", code, parentCode, dimension, weight, ruleText, scoreType,
                    page, tableId, sourceCellIds, quotedText, 0.95, ChildAggregation.SUM, "", "IN_SCHEME",
                    new com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata(ruleText, quotedText, List.of(), List.of(),
                            dimension, scoreType,
                            new com.xiyu.bid.scoreparse.domain.ModelSourceClaim("第三章 评审办法", page, tableId, sourceCellIds, "", ""))
            ));
        }

        String tenderText = "第三章 评审办法\n3.2.1 分值构成（总分100分）：（1）商务部分：15分；（2）技术部分：45分；（3）报价：40分。";
        OriginalScoringContext context = OriginalScoringContext.resolve(artifact, tenderText);
        var extractionResult = new MultimodalScoreFactExtractor.ExtractionResult(new BigDecimal("100"), rawFacts, "gemini-3.5-flash", 0);

        StructuredTableResult result = MultimodalStructuredExtractionHandler.run(
                "办公用品.pdf", tenderText, artifact, context, null, () -> {},
                extractionResult, true, true, true);

        assertThat(result.completeness()).isNotNull();
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.candidates()).hasSize(13);
        assertThat(result.completeness().leafWeightSum()).isEqualByComparingTo("100");

        // 5. 走持久化落库（模拟 StructuredTableExtractionHandler 落库阶段）
        ScoreItemPersistenceService persistenceService = new ScoreItemPersistenceService(
                itemRepository, resultRepository, factRepository, evidenceRepository, objectMapper);

        Long projectId = 1382L;
        Long taskId = 285L;
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

    @Test
    @DisplayName("项目1382线上真实Task286全量37项事实(含价格同义词+合同履约项)：无人工过滤 -> 判定AUTO_APPROVED -> 13项100分")
    void verifyTask286RealWorldFactsPipeline() throws Exception {
        DocumentArtifact artifact;
        try (InputStream is = getClass().getResourceAsStream("/scoreparse/project-1382/project-1382-scoring-tables.json")) {
            assertThat(is).as("Scoring tables resource must exist").isNotNull();
            artifact = objectMapper.readValue(is, DocumentArtifact.class);
        }
        artifact = SourceArtifactBundle.combine(List.of(artifact));

        JsonNode factsArray;
        try (InputStream is = getClass().getResourceAsStream("/scoreparse/project-1382/task-286-facts.json")) {
            assertThat(is).as("Task 286 facts resource must exist").isNotNull();
            factsArray = objectMapper.readTree(is);
        }

        java.util.Map<String, String> codeByFactId = new java.util.HashMap<>();
        for (JsonNode node : factsArray) {
            codeByFactId.put(node.path("factId").asText(""), node.path("code").asText(""));
        }

        java.util.Map<String, DocumentArtifact.CanonicalTable.CanonicalCellDto> cellMap = new java.util.HashMap<>();
        for (var t : artifact.tables()) {
            for (var c : t.cells()) {
                cellMap.put(c.cellId(), c);
            }
        }

        List<MultimodalScoreFact> rawFacts = new ArrayList<>();
        for (JsonNode node : factsArray) {
            String code = node.path("code").asText("");
            BigDecimal weight = new BigDecimal(node.path("weight").asText("0"));
            String ruleText = node.path("sourceText").asText("");
            String dimension = node.path("dimension").asText("");
            String scoreType = "OBJECTIVE";
            String parentFactId = node.path("parentFactId").asText("");
            String parentCode = parentFactId.isBlank() ? "" : codeByFactId.getOrDefault(parentFactId, "");
            String tableId = node.path("sourceTableId").asText("");

            List<String> sourceCellIds = new ArrayList<>();
            for (JsonNode cellNode : node.path("sourceCellIds")) {
                sourceCellIds.add(cellNode.asText());
            }

            Integer page = null;
            String quotedText = "";
            if (node.hasNonNull("weightEvidence")) {
                quotedText = node.path("weightEvidence").path("quotedText").asText("");
            }
            if (!sourceCellIds.isEmpty()) {
                var cell = cellMap.get(sourceCellIds.get(0));
                if (cell != null) {
                    page = cell.sourcePage();
                    if (quotedText.isBlank()) {
                        quotedText = cell.rawText() == null ? "" : cell.rawText();
                    }
                }
            }

            rawFacts.add(new MultimodalScoreFact(
                    "第三章 评审办法", code, parentCode, dimension, weight, ruleText, scoreType,
                    page, tableId, sourceCellIds, quotedText, 0.95, ChildAggregation.SUM, "", "",
                    new com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata(ruleText, quotedText, List.of(), List.of(),
                            dimension, scoreType,
                            new com.xiyu.bid.scoreparse.domain.ModelSourceClaim("第三章 评审办法", page, tableId, sourceCellIds, "", ""))
            ));
        }

        String tenderText = "第三章 评审办法\n3.2.1 分值构成（总分100分）：（1）商务部分：15分；（2）技术部分：45分；（3）报价：40分。\n"
                + "\n".repeat(5300)
                + "# 第五章 合同履约考核\n"
                + "协议商品动销率 考核\n";
        OriginalScoringContext context = OriginalScoringContext.resolve(artifact, tenderText);
        var extractionResult = new MultimodalScoreFactExtractor.ExtractionResult(new BigDecimal("100"), rawFacts, "gemini-3.5-flash", 0);

        StructuredTableResult result = MultimodalStructuredExtractionHandler.run(
                "办公用品.pdf", tenderText, artifact, context, null, () -> {},
                extractionResult, true, true, true);

        assertThat(result.completeness()).isNotNull();
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.candidates()).hasSize(13);
        assertThat(result.completeness().leafWeightSum()).isEqualByComparingTo("100");

        // 持久化落库
        ScoreItemPersistenceService persistenceService = new ScoreItemPersistenceService(
                itemRepository, resultRepository, factRepository, evidenceRepository, objectMapper);

        Long projectId = 1382L;
        Long taskId = 286L;
        persistenceService.persistItems(projectId, taskId, result.candidates());

        List<ScoreItem> persistedItems = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        assertThat(persistedItems).hasSize(13);
        BigDecimal totalWeightInDb = persistedItems.stream()
                .map(ScoreItem::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalWeightInDb).isEqualByComparingTo("100");

        var summary = new com.xiyu.bid.scoreparse.domain.SummaryAggregator().aggregate(
                persistedItems.stream().map(i -> new com.xiyu.bid.scoreparse.domain.SummaryAggregator.Item(
                        i.getWeight(), i.getScoreType(), i.getEstScore(), i.getStatusStage1())).toList());

        assertThat(summary.totalWeight()).isEqualByComparingTo("100");
        assertThat(summary.weightWarning()).isFalse();
    }

    @Test
    @DisplayName("项目1382线上真实Task289全量37项事实：本地100%无清洗重现 -> 验证两部精准修复 -> 判定AUTO_APPROVED -> 13项100分自动落库")
    void verifyTask289LiveOnlineFacts() throws Exception {
        DocumentArtifact artifact;
        try (InputStream is = getClass().getResourceAsStream("/scoreparse/project-1382/project-1382-scoring-tables.json")) {
            assertThat(is).as("Scoring tables resource must exist").isNotNull();
            artifact = objectMapper.readValue(is, DocumentArtifact.class);
        }
        artifact = SourceArtifactBundle.combine(List.of(artifact));

        JsonNode factsArray;
        try (InputStream is = getClass().getResourceAsStream("/scoreparse/project-1382/task-289-live-facts.json")) {
            assertThat(is).as("Task 289 facts resource must exist").isNotNull();
            factsArray = objectMapper.readTree(is);
        }

        java.util.Map<String, String> codeByFactId = new java.util.HashMap<>();
        for (JsonNode node : factsArray) {
            codeByFactId.put(node.path("factId").asText(""), node.path("code").asText(""));
        }

        java.util.Map<String, DocumentArtifact.CanonicalTable.CanonicalCellDto> cellMap = new java.util.HashMap<>();
        for (var t : artifact.tables()) {
            for (var c : t.cells()) {
                cellMap.put(c.cellId(), c);
            }
        }

        List<MultimodalScoreFact> rawFacts = new ArrayList<>();
        for (JsonNode node : factsArray) {
            String code = node.path("code").asText("");
            BigDecimal weight = new BigDecimal(node.path("weight").asText("0"));
            String ruleText = node.path("sourceText").asText("");
            String dimension = node.path("dimension").asText("");
            String scoreType = "OBJECTIVE";
            String parentFactId = node.path("parentFactId").asText("");
            String parentCode = parentFactId.isBlank() ? "" : codeByFactId.getOrDefault(parentFactId, "");
            String tableId = node.path("sourceTableId").asText("");

            List<String> sourceCellIds = new ArrayList<>();
            for (JsonNode cellNode : node.path("sourceCellIds")) {
                sourceCellIds.add(cellNode.asText());
            }

            Integer page = null;
            String quotedText = "";
            if (node.hasNonNull("weightEvidence")) {
                quotedText = node.path("weightEvidence").path("quotedText").asText("");
            }
            if (!sourceCellIds.isEmpty()) {
                var cell = cellMap.get(sourceCellIds.get(0));
                if (cell != null) {
                    page = cell.sourcePage();
                    if (quotedText.isBlank()) {
                        quotedText = cell.rawText() == null ? "" : cell.rawText();
                    }
                }
            }

            rawFacts.add(new MultimodalScoreFact(
                    "第三章 评审办法", code, parentCode, dimension, weight, ruleText, scoreType,
                    page, tableId, sourceCellIds, quotedText, 0.95, ChildAggregation.SUM, "", "",
                    new com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata(ruleText, quotedText, List.of(), List.of(),
                            dimension, scoreType,
                            new com.xiyu.bid.scoreparse.domain.ModelSourceClaim("第三章 评审办法", page, tableId, sourceCellIds, "", ""))
            ));
        }

        StringBuilder tenderTextBuilder = new StringBuilder();
        tenderTextBuilder.append("第三章 评审办法\n3.2.1 分值构成（总分100分）：（1）商务部分：15分；（2）技术部分：45分；（3）报价：40分。\n");
        for (int i = 2; i <= 6000; i++) {
            if (i == 1426) {
                tenderTextBuilder.append("（1）商务评分标准：见评审办法前附表；\n");
            } else if (i == 1427) {
                tenderTextBuilder.append("（2）技术评分标准：见评审办法前附表；\n");
            } else if (i == 1428) {
                tenderTextBuilder.append("（3）报价评分标准：\n报价得分可采用如下方法计算：\n方法一：偏差率=（供应商\n");
            } else if (i == 1440) {
                tenderTextBuilder.append("（4）其他因素评分标准：见评审办法前附表。\n");
            } else if (i == 5200) {
                tenderTextBuilder.append("# 第五章 合同条款及格式\n");
            } else if (i == 5275) {
                tenderTextBuilder.append("商品质量评价 5\n当B》=A时，得分权重为1，得分=1*订单评价的平均值（一星\n");
            } else if (i == 5280) {
                tenderTextBuilder.append("配送服务评价 5 全部5星\n考核指标 当B》=A时，得分权重为1，得分=1*订\n");
            } else if (i == 5285) {
                tenderTextBuilder.append("服务水平评价 5\n当B》=A时，得分权重为1，得分=1*订单评价的平均值（一星\n");
            } else if (i == 5303) {
                tenderTextBuilder.append("协议商品动销率 考核规则：动销率排名第1名的得满分，其余按照第1名动销率为基准进\n");
            } else if (i == 5308) {
                tenderTextBuilder.append("协议商品价格合\n15 系统根据自动计算 协议商品价格合格率（X）=当月度价格合\n");
            } else if (i == 5352) {
                tenderTextBuilder.append("根据供应商判断办公用品专区采购需求，供应商需回复所属专区采购需求（是否响应：是）\n");
            } else if (i == 5358) {
                tenderTextBuilder.append("系统自动计算 发货时效（X）=当月符合约定（审核人审核通过后48小时内）发货时效\n");
            } else if (i == 5361) {
                tenderTextBuilder.append("系统自动计算 办公用品专区分别计算：X=当月平均到货时效，Y=各供应商当月平均到\n");
            } else if (i == 5403) {
                tenderTextBuilder.append("收款核销考核 10 2026年每月25号进行计算：收款核销率（X）=累计已核销付\n");
            } else if (i == 5411) {
                tenderTextBuilder.append("系统及时 系统故障处理时 当故障发生时由平台正式向供应商报障，供应商故障处理人接\n");
            } else {
                tenderTextBuilder.append("\n");
            }
        }
        String tenderText = tenderTextBuilder.toString();
        OriginalScoringContext context = OriginalScoringContext.resolve(artifact, tenderText);
        var extractionResult = new MultimodalScoreFactExtractor.ExtractionResult(new BigDecimal("100"), rawFacts, "gemini-3.5-flash", 0);

        StructuredTableResult result = MultimodalStructuredExtractionHandler.run(
                "办公用品.pdf", tenderText, artifact, context, null, () -> {},
                extractionResult, true, true, true);

        assertThat(result.completeness()).isNotNull();
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.candidates()).hasSize(13);
        assertThat(result.completeness().leafWeightSum()).isEqualByComparingTo("100");

        // 持久化落库
        ScoreItemPersistenceService persistenceService = new ScoreItemPersistenceService(
                itemRepository, resultRepository, factRepository, evidenceRepository, objectMapper);

        Long projectId = 1382L;
        Long taskId = 289L;
        persistenceService.persistItems(projectId, taskId, result.candidates());

        List<ScoreItem> persistedItems = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        assertThat(persistedItems).hasSize(13);
        BigDecimal totalWeightInDb = persistedItems.stream()
                .map(ScoreItem::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalWeightInDb).isEqualByComparingTo("100");

        var summary = new com.xiyu.bid.scoreparse.domain.SummaryAggregator().aggregate(
                persistedItems.stream().map(i -> new com.xiyu.bid.scoreparse.domain.SummaryAggregator.Item(
                        i.getWeight(), i.getScoreType(), i.getEstScore(), i.getStatusStage1())).toList());

        assertThat(summary.totalWeight()).isEqualByComparingTo("100");
        assertThat(summary.weightWarning()).isFalse();
    }
}
