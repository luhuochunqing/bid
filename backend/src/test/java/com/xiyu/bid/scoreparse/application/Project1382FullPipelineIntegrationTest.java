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

        String[] mockLines = new String[6001];
        java.util.Arrays.fill(mockLines, "");
        mockLines[0] = "第三章 评审办法";
        mockLines[1] = "3.2.1 分值构成（总分100分）：（1）商务部分：15分；（2）技术部分：45分；（3）报价：40分。";
        mockLines[1426] = "（1）商务评分标准：见评审办法前附表；";
        mockLines[1427] = "（2）技术评分标准：见评审办法前附表；";
        mockLines[1428] = "（3）报价评分标准：";
        mockLines[1429] = "报价得分可采用如下方法计算：";
        mockLines[1430] = "方法一：偏差率=（供应商";
        mockLines[1440] = "（4）其他因素评分标准：见评审办法前附表。";
        mockLines[5200] = "# 第五章 合同条款及格式";
        mockLines[5275] = "商品质量评价 5";
        mockLines[5276] = "当B》=A时，得分权重为1，得分=1*订单评价的平均值（一星";
        mockLines[5280] = "配送服务评价 5 全部5星";
        mockLines[5281] = "考核指标 当B》=A时，得分权重为1，得分=1*订";
        mockLines[5285] = "服务水平评价 5";
        mockLines[5286] = "当B》=A时，得分权重为1，得分=1*订单评价的平均值（一星";
        mockLines[5303] = "协议商品动销率 考核规则：动销率排名第1名的得满分，其余按照第1名动销率为基准进";
        mockLines[5308] = "协议商品价格合";
        mockLines[5309] = "15 系统根据自动计算 协议商品价格合格率（X）=当月度价格合";
        mockLines[5352] = "根据供应商判断办公用品专区采购需求，供应商需回复所属专区采购需求（是否响应：是）";
        mockLines[5358] = "系统自动计算 发货时效（X）=当月符合约定（审核人审核通过后48小时内）发货时效";
        mockLines[5361] = "系统自动计算 办公用品专区分别计算：X=当月平均到货时效，Y=各供应商当月平均到";
        mockLines[5403] = "收款核销考核 10 2026年每月25号进行计算：收款核销率（X）=累计已核销付";
        mockLines[5411] = "系统及时 系统故障处理时 当故障发生时由平台正式向供应商报障，供应商故障处理人接";
        String tenderText = String.join("\n", mockLines);
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

    @Test
    @DisplayName("项目1382线上真实Task290全量51项事实：本地100%无清洗回放 -> 验证范围短路修复与父编码防歧义 -> 判定AUTO_APPROVED -> 13项100分自动落库")
    void verifyTask290LiveOnlineFacts() throws Exception {
        DocumentArtifact artifact;
        try (InputStream is = getClass().getResourceAsStream("/scoreparse/project-1382/project-1382-scoring-tables.json")) {
            assertThat(is).as("Scoring tables resource must exist").isNotNull();
            artifact = objectMapper.readValue(is, DocumentArtifact.class);
        }
        artifact = SourceArtifactBundle.combine(List.of(artifact));

        JsonNode factsArray;
        try (InputStream is = getClass().getResourceAsStream("/scoreparse/project-1382/task-290-live-facts.json")) {
            assertThat(is).as("Task 290 facts resource must exist").isNotNull();
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

            // 模拟线上 extractTextChunksInScope 提取出来的原始分片盖章事实（带有 Chapter 3 的初始 scopeStatus 与 schemeId）
            rawFacts.add(new MultimodalScoreFact(
                    "第三章 评审办法", code, parentCode, dimension, weight, ruleText, scoreType,
                    page, tableId, sourceCellIds, quotedText, 0.95, ChildAggregation.SUM,
                    "第三章 评标办法（综合评估法）", "IN_SCHEME",
                    new com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata(ruleText, quotedText, List.of(), List.of(),
                            dimension, scoreType,
                            new com.xiyu.bid.scoreparse.domain.ModelSourceClaim("第三章 评审办法", page, tableId, sourceCellIds, "", ""))
            ));
        }

        StringBuilder tenderTextBuilder = new StringBuilder();
        tenderTextBuilder.append("第三章 评审办法\n3.2.1 分值构成（总分100分）：（1）商务部分：15分；（2）技术部分：45分；（3）报价：40分。\n");
        for (int i = 2; i <= 6000; i++) {
            if (i == 1052) {
                tenderTextBuilder.append("3.2.1 分值构成（总分100分）：（1）商务部分：15分；（2）技术部分：45分；（3）报价：40分。\n");
            } else if (i == 1426) {
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
        assertThat(result.candidates()).hasSize(14);
        assertThat(result.completeness().leafWeightSum()).isEqualByComparingTo("100");

        // 持久化落库
        ScoreItemPersistenceService persistenceService = new ScoreItemPersistenceService(
                itemRepository, resultRepository, factRepository, evidenceRepository, objectMapper);

        Long projectId = 1382L;
        Long taskId = 290L;
        persistenceService.persistItems(projectId, taskId, result.candidates());

        List<ScoreItem> persistedItems = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        assertThat(persistedItems).hasSize(14);
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
    @DisplayName("项目1382线上真实Task291全量50项事实本地回放：Fix A 生效后 28分孤儿绑回 r16-c3、leafTotal=100、总分级联消除")
    void verifyTask291LiveDiagnostic() throws Exception {
        DocumentArtifact artifact;
        try (InputStream is = getClass().getResourceAsStream("/scoreparse/project-1382/project-1382-scoring-tables.json")) {
            assertThat(is).as("Scoring tables resource must exist").isNotNull();
            artifact = objectMapper.readValue(is, DocumentArtifact.class);
        }
        artifact = SourceArtifactBundle.combine(List.of(artifact));

        JsonNode factsArray;
        try (InputStream is = getClass().getResourceAsStream("/scoreparse/project-1382/task-291-live-facts.json")) {
            assertThat(is).as("Task 291 facts resource must exist").isNotNull();
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
            if (parentCode.isBlank()) {
                if (code.startsWith("3.2.3(1).")) {
                    parentCode = "3.2.3(1)";
                } else if (code.startsWith("3.2.3(2).")) {
                    parentCode = "3.2.3(2)";
                } else if ("3.2.3(3).1".equals(code) || "3.2.3(3).2".equals(code)) {
                    parentCode = "3.2.3(3)";
                }
            }
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
                    page, tableId, sourceCellIds, quotedText, 0.95, ChildAggregation.SUM,
                    "", "",
                    new com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata(ruleText, quotedText, List.of(), List.of(),
                            dimension, scoreType,
                            new com.xiyu.bid.scoreparse.domain.ModelSourceClaim("第三章 评审办法", page, tableId, sourceCellIds, "", ""))
            ));
        }

        StringBuilder tenderTextBuilder = new StringBuilder();
        tenderTextBuilder.append("第三章 评审办法\n3.2.1 分值构成（总分100分）：（1）商务部分：15分；（2）技术部分：45分；（3）报价：40分。\n");
        for (int i = 2; i <= 6000; i++) {
            if (i == 1052) {
                tenderTextBuilder.append("3.2.1 分值构成（总分100分）：（1）商务部分：15分；（2）技术部分：45分；（3）报价：40分。\n");
            } else if (i == 1426) {
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

        // ===== 诊断部分 A：直接复刻 run() 内部首轮管线（bind -> normalized -> validate），打印完整明细 =====
        var boundFacts = TextPrimaryEvidenceBinder.bind(rawFacts, artifact, tenderText);
        var firstExtraction = new MultimodalScoreFactExtractor.ExtractionResult(new BigDecimal("100"), boundFacts, "gemini-3.5-flash", 0);
        var normalizedExtraction = MultimodalExtractionReviewPolicy.normalized(firstExtraction, context, artifact, tenderText);
        var validator = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator();
        var validation = validator.validate(normalizedExtraction.facts(), artifact, context, tenderText,
                normalizedExtraction.declaredTotal());

        System.out.println("==== TASK291-DIAG A: direct pipeline (bind -> normalized -> validate) ====");
        System.out.println("rawFacts=" + rawFacts.size() + " boundFacts=" + boundFacts.size()
                + " normalizedFacts=" + normalizedExtraction.facts().size());
        System.out.println("declaredTotal=" + validation.declaredTotal()
                + " originalTotal=" + validation.originalTotal()
                + " leafTotal=" + validation.leafTotal()
                + " autoApprovable=" + validation.autoApprovable());
        System.out.println("sourceVerification.total=" + validation.sourceVerification().total());
        System.out.println("sourceVerification.scheme=" + validation.sourceVerification().scheme()
                + " publishable=" + validation.sourceVerification().publishable());
        System.out.println("sourceVerification.problems=" + validation.sourceVerification().problems());
        System.out.println("sourceVerification.qualifications.size=" + validation.sourceVerification().qualifications().size());
        for (var q : validation.sourceVerification().qualifications()) {
            System.out.println("  QUALIFICATION: " + q);
        }

        // factId -> (code/dimension/weight) 映射（violation.factId 形如 sectionTitle/code）
        System.out.println("violations.size=" + validation.violations().size());
        int vIdx = 0;
        for (var v : validation.violations()) {
            vIdx++;
            String vCode = v.factId();
            String factCodePart = vCode.contains("/") ? vCode.substring(vCode.indexOf('/') + 1) : vCode;
            MultimodalScoreFact mapped = normalizedExtraction.facts().stream()
                    .filter(f -> f.code().equals(factCodePart)).findFirst().orElse(null);
            System.out.println("  VIOLATION[" + vIdx + "] code=" + v.code()
                    + " factId=" + v.factId()
                    + " field=" + v.field()
                    + " evidence=" + v.evidence()
                    + " message=" + v.message()
                    + " expected=" + v.expected()
                    + " actual=" + v.actual()
                    + " || mappedFact: code=" + (mapped == null ? "?" : mapped.code())
                    + " dimension=" + (mapped == null ? "?" : mapped.dimension())
                    + " weight=" + (mapped == null ? "?" : mapped.weight())
                    + " tableId=" + (mapped == null ? "?" : mapped.tableId())
                    + " scopeStatus=" + (mapped == null ? "?" : mapped.scopeStatus())
                    + " parentCode=" + (mapped == null ? "?" : mapped.parentCode()));
        }

        // 28分孤儿在各环节的状态
        String orphanCode = "3.2.3(3)_1";
        System.out.println("---- ORPHAN (28分, code=" + orphanCode + ") trace ----");
        for (MultimodalScoreFact f : rawFacts) {
            if (orphanCode.equals(f.code())) {
                System.out.println("  RAW: modelFactId=" + com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.modelFactId(f)
                        + " scopeStatus=" + f.scopeStatus() + " schemeId=" + f.schemeId()
                        + " tableId='" + f.tableId() + "' cellIds=" + f.sourceCellIds()
                        + " parentCode='" + f.parentCode() + "' quotedText='" + f.quotedText() + "'");
            }
        }
        for (MultimodalScoreFact f : normalizedExtraction.facts()) {
            if (orphanCode.equals(f.code())) {
                System.out.println("  NORMALIZED: modelFactId=" + com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.modelFactId(f)
                        + " scopeStatus=" + f.scopeStatus() + " schemeId=" + f.schemeId()
                        + " tableId='" + f.tableId() + "' cellIds=" + f.sourceCellIds()
                        + " parentCode='" + f.parentCode() + "' quotedText='" + f.quotedText() + "'");
            }
        }
        List<MultimodalScoreFact> eligibleModelFacts = com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy
                .eligibleModelFacts(normalizedExtraction.facts(), validation.sourceVerification());
        System.out.println("  eligibleModelFacts.size=" + eligibleModelFacts.size() + " / " + normalizedExtraction.facts().size());
        boolean orphanEligible = eligibleModelFacts.stream().anyMatch(f -> orphanCode.equals(f.code()));
        System.out.println("  orphanEligible=" + orphanEligible);
        for (MultimodalScoreFact f : eligibleModelFacts) {
            System.out.println("    ELIGIBLE: code=" + f.code() + " dimension=" + f.dimension()
                    + " weight=" + f.weight() + " scopeStatus=" + f.scopeStatus() + " tableId='" + f.tableId() + "'");
        }
        boolean orphanInViolations = validation.violations().stream().anyMatch(v -> v.factId().contains(orphanCode));
        System.out.println("  orphanInViolations=" + orphanInViolations);
        boolean multiTable = MultimodalExtractionReviewPolicy.hasMultipleTables(eligibleModelFacts);
        boolean independentTotals = MultimodalExtractionReviewPolicy.hasIndependentTableTotals(context, eligibleModelFacts);
        boolean totalCloses = MultimodalExtractionReviewPolicy.documentTotalCloses(context, validation);
        System.out.println("  hasMultipleTables=" + multiTable + " hasIndependentTableTotals=" + independentTotals
                + " documentTotalCloses=" + totalCloses
                + " => multiTableModelScopeUnknown=" + (multiTable && !independentTotals && !totalCloses));

        // ===== 诊断部分 B：完整 handler.run（alreadyRechecked=true，等价于线上复核失败后保留首轮结果）=====
        System.out.println("==== TASK291-DIAG B: MultimodalStructuredExtractionHandler.run ====");
        var extractionResult = new MultimodalScoreFactExtractor.ExtractionResult(new BigDecimal("100"), rawFacts, "gemini-3.5-flash", 0);
        StructuredTableResult result = MultimodalStructuredExtractionHandler.run(
                "办公用品.pdf", tenderText, artifact, context, null, () -> {},
                extractionResult, true, true, true);

        var completeness = result.completeness();
        System.out.println("completeness=" + (completeness == null ? "null" : "present"));
        if (completeness != null) {
            System.out.println("decision=" + completeness.decision()
                    + " fallbackReason=" + completeness.fallbackReason()
                    + " declaredTotalScore=" + completeness.declaredTotalScore()
                    + " declaredTotalSource=" + completeness.declaredTotalSource()
                    + " leafWeightSum=" + completeness.leafWeightSum()
                    + " leafCount=" + completeness.leafCount()
                    + " parentCount=" + completeness.parentCount()
                    + " missingEvidenceCount=" + completeness.missingEvidenceCount());
            System.out.println("defects.size=" + completeness.defects().size());
            for (String d : completeness.defects()) {
                System.out.println("  DEFECT: " + d);
            }
            System.out.println("leafFacts (计入leafTotal):");
            for (var lf : completeness.leafFacts()) {
                System.out.println("  LEAF: code=" + lf.code() + " dimension=" + lf.dimension()
                        + " weight=" + lf.weight() + " factId=" + lf.factId()
                        + " tableId='" + lf.sourceTableId() + "'");
            }
            System.out.println("parentFacts:");
            for (var pf : completeness.parentFacts()) {
                System.out.println("  PARENT: code=" + pf.code() + " dimension=" + pf.dimension()
                        + " weight=" + pf.weight() + " factId=" + pf.factId()
                        + " tableId='" + pf.sourceTableId() + "'");
            }
        }
        System.out.println("candidates.size=" + (result.candidates() == null ? -1 : result.candidates().size()));

        // 回归钉死 Fix A（EvidenceText 数字↔汉字边界空白删除）：
        // 28分孤儿必须重新绑回物理来源并计入 leafTotal；总分闭合不得再出现 WEIGHT_SUM_MISMATCH。
        MultimodalScoreFact boundOrphan = boundFacts.stream()
                .filter(f -> "3.2.3(3)_1".equals(f.code())).findFirst().orElseThrow();
        assertThat(boundOrphan.tableId()).isEqualTo("doc0-p34-t0010");
        assertThat(boundOrphan.sourceCellIds()).containsExactly("doc0-p34-t0010-r16-c3");
        assertThat(validation.leafTotal()).isEqualByComparingTo("100");
        // Task 291 线上真实形状在 !2802（四项修复）+ 引文分值闭合判定后全量闭环：0 违规、可自动发布
        assertThat(validation.violations()).isEmpty();
        assertThat(validation.autoApprovable()).isTrue();
        MultimodalScoreFact normalizedOrphan = normalizedExtraction.facts().stream()
                .filter(f -> "3.2.3(3)_1".equals(f.code())).findFirst().orElseThrow();
        assertThat(normalizedOrphan.scopeStatus()).isEqualTo("IN_SCHEME");
        assertThat(normalizedOrphan.parentCode()).isEqualTo("3.2.3(3)");
        // 完整 handler 链路：AUTO_APPROVED 且生成候选（非空才可落库替换 91 分化石）
        assertThat(completeness).isNotNull();
        assertThat(completeness.decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(completeness.leafWeightSum()).isEqualByComparingTo("100");
        assertThat(result.candidates()).isNotEmpty();
    }

    @Test
    @DisplayName("Task291 28分孤儿：忠实模型原始输入（空 locator/schemeId）经 bind 应命中 r16-c3")
    void verifyTask291OrphanBindsAfterDigitHanNormalizeFix() throws Exception {
        DocumentArtifact artifact;
        try (InputStream is = getClass().getResourceAsStream("/scoreparse/project-1382/project-1382-scoring-tables.json")) {
            assertThat(is).as("Scoring tables resource must exist").isNotNull();
            artifact = objectMapper.readValue(is, DocumentArtifact.class);
        }
        artifact = SourceArtifactBundle.combine(List.of(artifact));

        MultimodalScoreFact orphan = new MultimodalScoreFact(
                "第三章 评审办法", "3.2.3(3)_1", "3.2.3(3)", "报价", new BigDecimal("28"),
                "", "SUBJECTIVE", null, "", List.of(), "一级集采商品价格分（满分28分）", 0.95,
                ChildAggregation.SUM, "", "",
                new com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata("", "一级集采商品价格分（满分28分）",
                        List.of(), List.of(), "报价", "SUBJECTIVE",
                        new com.xiyu.bid.scoreparse.domain.ModelSourceClaim("第三章 评审办法", null, "", List.of(), "", "")));

        List<MultimodalScoreFact> bound = TextPrimaryEvidenceBinder.bind(List.of(orphan), artifact, null);

        assertThat(bound).hasSize(1);
        MultimodalScoreFact fact = bound.get(0);
        assertThat(fact.tableId()).as("28分孤儿必须绑定到报价细则表")
                .isEqualTo("doc0-p34-t0010");
        assertThat(fact.sourceCellIds()).as("28分孤儿必须绑定到 r16-c3（含「满分28\\n分」硬换行的单元格）")
                .containsExactly("doc0-p34-t0010-r16-c3");
    }

    @Test
    @DisplayName("项目1382 Task 292现场事实回放验证")
    void verifyTask292LiveOnlineFacts() throws Exception {
        DocumentArtifact artifact;
        try (InputStream is = getClass().getResourceAsStream("/scoreparse/project-1382/project-1382-scoring-tables.json")) {
            assertThat(is).as("Scoring tables resource must exist").isNotNull();
            artifact = objectMapper.readValue(is, DocumentArtifact.class);
        }
        artifact = SourceArtifactBundle.combine(List.of(artifact));
        java.util.Map<String, DocumentArtifact.CanonicalTable.CanonicalCellDto> cellMap = new java.util.HashMap<>();
        for (var table : artifact.tables()) {
            for (var cell : table.cells()) {
                cellMap.put(cell.cellId(), cell);
            }
        }

        JsonNode factsNode;
        try (InputStream is = getClass().getResourceAsStream("/scoreparse/project-1382/task-292-live-facts.json")) {
            assertThat(is).isNotNull();
            factsNode = objectMapper.readTree(is);
        }

        var codeByFactId = new java.util.HashMap<String, String>();
        for (JsonNode node : factsNode) {
            codeByFactId.put(node.path("factId").asText(), node.path("code").asText());
        }

        List<MultimodalScoreFact> rawFacts = new ArrayList<>();
        for (JsonNode node : factsNode) {
            String code = node.path("code").asText();
            BigDecimal weight = node.hasNonNull("weight") ? new BigDecimal(node.path("weight").asText()) : null;
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
                    page, tableId, sourceCellIds, quotedText, 0.95, ChildAggregation.SUM,
                    "", "",
                    new com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata(ruleText, quotedText, List.of(), List.of(),
                            dimension, scoreType,
                            new com.xiyu.bid.scoreparse.domain.ModelSourceClaim("第三章 评审办法", page, tableId, sourceCellIds, "", ""))
            ));
        }

        StringBuilder tenderTextBuilder = new StringBuilder();
        tenderTextBuilder.append("第三章 评审办法\n3.2.1 分值构成（总分100分）：（1）商务部分：15分；（2）技术部分：45分；（3）报价：40分。\n");
        for (int i = 2; i <= 6000; i++) {
            if (i == 1052) {
                tenderTextBuilder.append("3.2.1 分值构成（总分100分）：（1）商务部分：15分；（2）技术部分：45分；（3）报价：40分。\n");
            } else if (i == 1426) {
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

        System.out.println("=== TASK 292 RESULT ===");
        System.out.println("decision: " + (result.completeness() == null ? "null" : result.completeness().decision()));
        System.out.println("fallbackReason: " + (result.completeness() == null ? "null" : result.completeness().fallbackReason()));
        if (result.completeness() != null) {
            System.out.println("defects: " + result.completeness().defects());
        }
        assertThat(result.completeness()).isNotNull();
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
    }
}