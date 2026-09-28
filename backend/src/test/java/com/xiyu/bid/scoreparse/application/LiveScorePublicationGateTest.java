package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Live 1382 parses keep preface rows and a null model total. Sanitized fixtures hid both. */
class LiveScorePublicationGateTest {

    private static final Map<String, String> SHARED_CELL_QUOTES = Map.of(
            "business_score", "（1）商务部分：15分",
            "technical_score", "（2）技术部分：45分",
            "price_score", "（3）报价：40分",
            "price_level1", "1、一级集采商品上架折扣率（28分）",
            "price_non_level1", "1、非一级集采商品上架折扣率（12分）");
    private static final Map<String, String> POINTER_QUOTES = Map.of(
            "商务评分标准", "（1）商务评分标准：见评审办法前附表；",
            "技术评分标准", "（2）技术评分标准：见评审办法前附表；",
            "报价评分标准", "（3）报价评分标准：报价得分可采用如下方法计算。",
            "其他因素评分标准", "（4）其他因素评分标准：见评审办法前附表。");

    @Test
    void categoryRowAndPrefacePointerPublishWhenModelOmitsTotal() {
        String tender = """
                本项目评分总分3分。
                （1）商务评分标准：见评审办法前附表；
                """;
        var artifact = new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("bid.pdf", "sha-live", "pdf", "2.2"),
                List.of(new DocumentArtifact.CanonicalTable("t1", 3, "本项目评分办法", 2, 3, List.of(), List.of(
                        cell("n", 1, 0, "财务状况（3分）"),
                        cell("r", 1, 1, "现金流量净额连续三年为正的得1.5分，有两年为正的得1分"),
                        cell("w", 1, 2, "3分")), true)),
                List.of());
        var context = OriginalScoringContext.resolve(artifact, tender);
        String schemeId = OriginalScoringContext.schemeId(context.scheme());
        var facts = List.of(
                fact("本项目评分办法", "cw", "", "商务", "3",
                        "现金流量净额连续三年为正的得1.5分，有两年为正的得1分",
                        3, "t1", List.of("r"), "", ""),
                fact("本项目评分办法", "preface", "", "商务评分标准", "0",
                        "（1）商务评分标准：见评审办法前附表；",
                        null, "", List.of("text:line[2]"), schemeId, "IN_SCHEME"));

        var result = MultimodalStructuredExtractionHandler.run(
                "办公用品.pdf", tender, artifact, context, null, () -> {},
                new MultimodalScoreFactExtractor.ExtractionResult(null, facts, "gemini-3.5-flash", 0),
                true, false, true);

        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.completeness().defects()).isEmpty();
        assertThat(result.completeness().leafWeightSum()).isEqualByComparingTo("3");
    }

    @Test
    void disagreeingModelTotalStaysInReview() {
        String tender = "本项目评分总分3分。";
        var artifact = new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("bid.pdf", "sha-conflict", "pdf", "2.2"),
                List.of(new DocumentArtifact.CanonicalTable("t1", 3, "本项目评分办法", 2, 3, List.of(), List.of(
                        cell("n", 1, 0, "财务状况（3分）"),
                        cell("r", 1, 1, "现金流量净额连续三年为正的得1.5分"),
                        cell("w", 1, 2, "3分")), true)),
                List.of());
        var context = OriginalScoringContext.resolve(artifact, tender);
        var facts = List.of(fact("本项目评分办法", "cw", "", "商务", "3",
                "现金流量净额连续三年为正的得1.5分", 3, "t1", List.of("r"), "", ""));

        var result = MultimodalStructuredExtractionHandler.run(
                "办公用品.pdf", tender, artifact, context, null, () -> {},
                new MultimodalScoreFactExtractor.ExtractionResult(new BigDecimal("9"), facts, "gemini-3.5-flash", 0),
                true, false, true);

        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.completeness().defects().toString()).contains("MODEL_TOTAL_SOURCE_MISMATCH");
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void project1382OnlineFactsKeepPrefaceRowsAndNullModelTotal() throws Exception {
        DocumentArtifact artifact;
        try (InputStream in = getClass().getResourceAsStream("/scoreparse/project-1382/project-1382-scoring-tables.json")) {
            artifact = new ObjectMapper().readValue(in, DocumentArtifact.class);
        }
        artifact = SourceArtifactBundle.combine(List.of(artifact));
        String tender = """
                第三章 评审办法
                3.2.1 分值构成（总分100分）：（1）商务部分：15分；（2）技术部分：45分；（3）报价：40分。
                （1）商务评分标准：见评审办法前附表；
                （2）技术评分标准：见评审办法前附表；
                （3）报价评分标准：报价得分可采用如下方法计算。
                （4）其他因素评分标准：见评审办法前附表。
                """;
        var context = OriginalScoringContext.resolve(artifact, tender);
        var facts = loadOnlineFacts(artifact, OriginalScoringContext.schemeId(context.scheme()));

        var result = MultimodalStructuredExtractionHandler.run(
                "办公用品.pdf", tender, artifact, context, null, () -> {},
                new MultimodalScoreFactExtractor.ExtractionResult(null, facts, "gemini-3.5-flash", 0),
                true, false, true);

        assertThat(result.completeness().decision()).as(result.completeness().defects().toString())
                .isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.completeness().leafWeightSum()).isEqualByComparingTo("100");
        assertThat(result.candidates()).hasSize(13);
        assertThat(result.candidates().stream().map(c -> c.weight()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100");
        assertThat(result.candidates()).anySatisfy(c -> assertThat(c.dim()).isEqualTo("供应商业绩"));
        assertThat(result.candidates()).anySatisfy(c -> assertThat(c.dim()).isEqualTo("财务状况"));
        assertThat(result.candidates()).anySatisfy(c -> assertThat(c.dim()).isEqualTo("服务网点及仓储"));
        assertThat(result.candidates()).noneSatisfy(c -> assertThat(c.weight()).isEqualByComparingTo("0"));
    }

    @Test
    void publishedDimensionReadsTheTitleNotTheCategoryWord() {
        assertThat(OriginalScoreEvidencePolicy.publishedDimension(
                "商务", "财务状况（3分）\n1.供应商得1.5分", new BigDecimal("3"))).isEqualTo("财务状况");
        assertThat(OriginalScoreEvidencePolicy.publishedDimension(
                "销售额", "财务状况（3分）", new BigDecimal("3"))).isEqualTo("销售额");
    }

    private static List<MultimodalScoreFact> loadOnlineFacts(DocumentArtifact artifact, String schemeId) throws Exception {
        JsonNode facts;
        try (InputStream in = LiveScorePublicationGateTest.class.getResourceAsStream(
                "/scoreparse/project-1382/task-285-facts.json")) {
            facts = new ObjectMapper().readTree(in);
        }
        Map<String, String> codeByFactId = new java.util.HashMap<>();
        for (JsonNode node : facts) codeByFactId.put(node.path("factId").asText(""), node.path("code").asText(""));
        Map<String, DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new java.util.HashMap<>();
        for (var table : artifact.tables()) {
            for (var cell : table.cells()) cells.put(cell.cellId(), cell);
        }
        List<MultimodalScoreFact> loaded = new ArrayList<>();
        int line = 2;
        for (JsonNode node : facts) {
            String tableId = node.path("sourceTableId").asText("");
            BigDecimal weight = new BigDecimal(node.path("weight").asText("0"));
            String code = node.path("code").asText("");
            if (tableId.isBlank() && weight.signum() > 0) continue;
            String parentFactId = node.path("parentFactId").asText("");
            String parentCode = parentFactId.isBlank() ? "" : codeByFactId.getOrDefault(parentFactId, "");
            if (weight.signum() <= 0) {
                String quote = POINTER_QUOTES.getOrDefault(code, "（1）商务评分标准：见评审办法前附表；");
                loaded.add(fact("第三章 评审办法", code, parentCode, node.path("dimension").asText(""),
                        weight.toPlainString(), quote, null, "", List.of("text:line[" + line++ + "]"),
                        schemeId, "IN_SCHEME"));
                continue;
            }
            List<String> cellIds = new ArrayList<>();
            node.path("sourceCellIds").forEach(id -> cellIds.add(id.asText()));
            var cell = cells.get(cellIds.get(0));
            String quote = SHARED_CELL_QUOTES.getOrDefault(code, cell.rawText());
            loaded.add(fact("第三章 评审办法", code, parentCode, node.path("dimension").asText(""),
                    weight.toPlainString(), quote, cell.sourcePage(), tableId, cellIds, "", ""));
        }
        return loaded;
    }

    private static MultimodalScoreFact fact(String section, String code, String parent, String dimension,
                                             String weight, String quote, Integer page, String tableId,
                                             List<String> cells, String schemeId, String scope) {
        return new MultimodalScoreFact(section, code, parent, dimension, new BigDecimal(weight), quote,
                "OBJECTIVE", page, tableId, cells, quote, 0.95, ChildAggregation.SUM, schemeId, scope);
    }

    private static DocumentArtifact.CanonicalTable.CanonicalCellDto cell(String id, int row, int column, String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, row, column, text, text, 1, 1, false, "");
    }
}
