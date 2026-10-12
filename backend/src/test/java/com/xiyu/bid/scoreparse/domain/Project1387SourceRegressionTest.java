package com.xiyu.bid.scoreparse.domain;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class Project1387SourceRegressionTest {
    public void verifyGoldenSource() throws Exception {
        realScoringChapterWinsOverChapterNamesListedInComposition();
        originalGroupedTableClosesOneHundredWithoutModelTotal();
    }
    public static DocumentArtifact fixtureArtifact() throws Exception {
        String dir = System.getProperty("scoreparse1387.input");
        String json = dir == null ? resource(".artifact.json") : Files.readString(Path.of(dir, "artifact.json"));
        return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(json, DocumentArtifact.class);
    }
    public static String fixtureText() throws Exception {
        String dir = System.getProperty("scoreparse1387.input");
        return dir == null ? resource(".md") : Files.readString(Path.of(dir, "tender.md"));
    }
    private static String resource(String suffix) throws Exception {
        try (var stream = Project1387SourceRegressionTest.class.getResourceAsStream(
                "/scoreparse-golden/case-project-1387-grouped-source" + suffix)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    @Test void realScoringChapterWinsOverChapterNamesListedInComposition() throws Exception {
        String text = fixtureText();
        int line = lineContaining(text, "经营及财务状况 | 5");
        assertThat(OriginalScoringContext.textScopeAt(text, line).scopeStatus()).isEqualTo("IN_SCHEME");
        int outside = lastLineContaining(text, "第四章 合同条款及格式");
        assertThat(OriginalScoringContext.textScopeAt(text, outside).scopeStatus()).isEqualTo("OUT_OF_SCHEME");
    }
    @Test void originalGroupedTableClosesOneHundredWithoutModelTotal() throws Exception {
        var context = OriginalScoringContext.resolve(fixtureArtifact(), fixtureText());
        assertThat(context.total().value()).isEqualByComparingTo("100");
        assertThat(context.total().locator()).contains("t-0005");
        assertThat(context.total().quote()).contains("商务部分", "30", "技术部分", "40", "价格部分");
    }
    @Test void missingOrDuplicateGroupCannotInventTotal() throws Exception {
        assertThat(contextReplacing("价格部分", "其他项目").total().value()).isNull();
        assertThat(contextReplacing("价格部分", "商务部分").total().value()).isNull();
    }
    @Test void childWeightsMustCloseTheirGroup() throws Exception {
        assertThat(contextReplacing("\"rawText\":\"15\"", "\"rawText\":\"14\"").total().value()).isNull();
    }
    @Test void laterMarkdownHeadingMustNotEraseRealBareContractBoundary() {
        String text = "# 第三章 评审办法\n评分\n第四章 合同条款\n合同内容\n# 第四章 合同条款\n后文";
        assertThat(OriginalScoringContext.textScopeAt(text, 3).scopeStatus()).isEqualTo("OUT_OF_SCHEME");
    }
    @Test void amountColumnCannotSupplyGroupWeights() throws Exception {
        assertThat(contextReplacing("分值", "金额").total().value()).isNull();
    }
    @Test void groupsFromSeparateTablesCannotSupplyOneTotal() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.readTree(mapper.writeValueAsString(fixtureArtifact()));
        var tables = (com.fasterxml.jackson.databind.node.ArrayNode) root.path("tables");
        var original = java.util.stream.StreamSupport.stream(tables.spliterator(), false)
                .filter(table -> table.path("tableId").asText().equals("t-0005")).findFirst().orElseThrow();
        var priceTable = original.deepCopy();
        var firstCells = (com.fasterxml.jackson.databind.node.ArrayNode) original.path("cells");
        var priceCells = (com.fasterxml.jackson.databind.node.ArrayNode) priceTable.path("cells");
        for (int i = firstCells.size() - 1; i >= 0; i--)
            if (firstCells.get(i).path("row").asInt() == 14) firstCells.remove(i);
        for (int i = priceCells.size() - 1; i >= 0; i--) {
            int row = priceCells.get(i).path("row").asInt();
            if (row != 0 && row != 14) priceCells.remove(i);
            else if (row == 14) ((com.fasterxml.jackson.databind.node.ObjectNode) priceCells.get(i)).put("row", 1);
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) original).put("rows", 14);
        ((com.fasterxml.jackson.databind.node.ObjectNode) priceTable).put("rows", 2).put("tableId", "price-table");
        tables.add(priceTable);
        assertThat(OriginalScoringContext.resolve(mapper.treeToValue(root, DocumentArtifact.class), fixtureText())
                .total().value()).isNull();
    }
    @Test void unnumberedRowsNeedIndependentNameAndNumericScore() throws Exception {
        var artifact = fixtureArtifact();
        assertThat(LogicalRuleSourceUnit.closed(artifact, java.util.List.of("t-0005-r2-c0"))).isTrue();
        for (boolean merged : new boolean[]{false, true}) {
            var mapper = new ObjectMapper();
            var root = mapper.readTree(mapper.writeValueAsString(artifact));
            for (var table : root.path("tables")) for (var cell : table.path("cells")) {
                if (cell.path("cellId").asText().equals("t-0005-r3-c1")) {
                    var changed = (com.fasterxml.jackson.databind.node.ObjectNode) cell;
                    if (merged) changed.put("isMergedContinue", true);
                    else changed.put("rawText", "");
                }
            }
            assertThat(LogicalRuleSourceUnit.closed(mapper.treeToValue(root, DocumentArtifact.class),
                    java.util.List.of("t-0005-r2-c0"))).isFalse();
        }
    }
    @Test void unnumberedLastRowCannotHideSameChapterOrUnknownContinuation() throws Exception {
        var artifact = fixtureArtifact();
        var table = artifact.tables().stream().filter(t -> t.tableId().equals("t-0005")).findFirst().orElseThrow();
        for (String section : new String[]{"第三章 评审办法 > 附表四", ""}) {
            var next = new DocumentArtifact.CanonicalTable("next", null, section, 1, 3, java.util.List.of(),
                    java.util.List.of(new DocumentArtifact.CanonicalTable.CanonicalCellDto("next-rule", 0, 2,
                            "续页内容", "续页内容", 1, 1, false, "fixture:next")), false);
            var input = new DocumentArtifact(artifact.artifact(), java.util.List.of(table, next), java.util.List.of());
            assertThat(LogicalRuleSourceUnit.closed(input, java.util.List.of("t-0005-r14-c0"))).isFalse();
        }
    }
    @Test void bareHeadingsRemainSupportedWhenNoMarkdownChapterExists() {
        var scopes = OriginalScoringContext.textScopes("第三章 评审办法\n评分内容\n第四章 合同条款及格式\n合同内容");
        assertThat(scopes.getFirst().scopeStatus()).isEqualTo("IN_SCHEME");
        assertThat(scopes.getLast().scopeStatus()).isEqualTo("OUT_OF_SCHEME");
    }
    private static OriginalScoringContext contextReplacing(String from, String to) throws Exception {
        var mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(fixtureArtifact());
        json = json.replace(from, to);
        return OriginalScoringContext.resolve(mapper.readValue(json, DocumentArtifact.class), fixtureText());
    }
    private static int lastLineContaining(String text, String part) {
        String[] lines = text.split("\\R", -1);
        for (int i = lines.length - 1; i >= 0; i--)
            if (lines[i].replace("**", "").contains(part)) return i;
        throw new AssertionError("missing source line: " + part);
    }
    private static int lineContaining(String text, String part) {
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) if (lines[i].replace("**", "").contains(part)) return i;
        throw new AssertionError("missing source line: " + part);
    }
}
