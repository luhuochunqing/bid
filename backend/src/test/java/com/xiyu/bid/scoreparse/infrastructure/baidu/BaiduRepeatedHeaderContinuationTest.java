package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class BaiduRepeatedHeaderContinuationTest {

    @Test void evaluationScheduleStartsAfterPrefaceAndKeepsRuleOnlyContinuation() {
        var header = new String[]{"序号", "评议类别", "评价事项", "", "评价要素及分值"};
        var preface = table("preface", 8, "评分规则",
                new String[]{"条款", "", "内容", "", "见评分明细"});
        var details = table("detail", 9, "评分规则", header,
                new String[]{"1", "技术评议（10分）", "服务", "5", "按下列规则评定。"});
        var next = table("next", 10, "评分规则", header,
                new String[]{"", "", "", "", "同时提交有效承诺。"},
                new String[]{"", "", "配送", "5", "按响应时限评定。"});
        var result = BaiduCrossPageTableLinker.mergeCrossPageScoreTables(List.of(preface, details, next));
        assertThat(result).extracting(CanonicalTable::tableId).containsExactly("preface", "detail");
        assertThat(result.getLast().cells()).filteredOn(c -> "同时提交有效承诺。".equals(c.rawText()))
                .singleElement().satisfies(c -> assertThat(c.logicalRowId()).isEqualTo("detail-r1"));
    }

    @Test void reviewerInstructionInRuleColumnIsNotAnItemContinuation() {
        var first = table("first", 9, "评分规则", new String[]{"类别", "评分项", "分值", "评分标准"},
                new String[]{"技术", "服务", "5", "提供完整方案得5分"});
        var response = new BaiduTableModels.BaiduTableResponse(1, List.of(new BaiduTableModels.BaiduTableItem(
                List.of(), List.of(), List.of(new BaiduTableModels.BaiduTableCell(0, 1, 3, 4,
                        "注：评委独立评分，按算术平均分汇总", List.of())), List.of())), null, null, 1L);
        var note = BaiduTableArtifactAdapter.toCanonicalTables(response, 10, "评分规则").getFirst();
        assertThat(note.isScoreTable()).isTrue();
        var linked = BaiduCrossPageTableLinker.mergeCrossPageScoreTables(List.of(first, note), java.util.Map.of(9, first, 10, first));
        assertThat(BaiduCrossPageTableLinker.linkCrossPageLogicalRows(linked).getLast().cells())
                .filteredOn(c -> c.rawText().contains("算术平均"))
                .allSatisfy(c -> assertThat(c.logicalRowId()).isNotEqualTo("first-r1"));
        var artifact = new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact(
                new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta("synthetic", "sha", "PDF", "fixture"), linked, List.of());
        assertThat(com.xiyu.bid.scoreparse.application.ScoreFactShadowPipeline.run(artifact).tables().stream()
                .flatMap(t -> t.facts().stream())).allSatisfy(f -> assertThat(f.sourceText()).doesNotContain("算术平均"));
    }

    public static void replayGoldenFixture(String markdown, com.fasterxml.jackson.databind.JsonNode expected) {
        var tables = new ArrayList<CanonicalTable>();
        for (String block : markdown.split("(?m)^## ")) {
            if (!block.matches("(?s)^\\d+\\s.*")) continue;
            int page = Integer.parseInt(block.lines().findFirst().orElseThrow().strip());
            var rows = block.lines().filter(s -> s.startsWith("|") && !s.matches("[| :\\-]+"))
                    .map(s -> java.util.Arrays.stream(s.split("\\|", -1)).skip(1).limit(4).map(String::strip).toArray(String[]::new))
                    .toArray(String[][]::new);
            var source = table("p" + page, page, "评分规则", rows);
            tables.add(new CanonicalTable(source.tableId(), page, source.sourceSection(), source.rows(), source.columns(),
                    source.mergedRegions(), source.cells(), page != expected.path("unclassifiedPage").asInt(), "PDF"));
        }
        assertThat(tables).hasSize(2);
        var first = tables.getFirst(); var next = tables.getLast();
        assertThat(BaiduCrossPageTableLinker.mergeCrossPageScoreTables(tables)).hasSize(2);
        var linked = BaiduCrossPageTableLinker.mergeCrossPageScoreTables(tables,
                java.util.Map.of(first.sourcePage(), first, next.sourcePage(), first));
        assertThat(linked).hasSize(1);
        assertThat(linked.getFirst().cells()).filteredOn(c -> expected.path("tail").asText().equals(c.rawText()))
                .singleElement().satisfies(c -> assertThat(c.logicalRowId()).isEqualTo(expected.path("logicalRow").asText()));
        var artifact = new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact(
                new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta("synthetic", "sha", "PDF", "fixture"), linked, List.of());
        var facts = com.xiyu.bid.scoreparse.application.ScoreFactShadowPipeline.run(artifact).tables().stream()
                .flatMap(t -> t.facts().stream()).toList();
        assertThat(facts).filteredOn(f -> "服务".equals(f.dimension())).singleElement()
                .satisfies(f -> assertThat(f.sourceText()).contains(expected.path("tail").asText()));
        assertThat(BaiduScanPageSelector.determinePagesToScan(artifact, List.of(first.sourcePage()), 50))
                .containsExactlyElementsOf(java.util.stream.StreamSupport.stream(expected.path("pages").spliterator(), false)
                        .map(com.fasterxml.jackson.databind.JsonNode::asInt).toList());
    }

    @Test void headerlessNonScoreClassificationDoesNotEraseKnownSourceSection() {
        var response = new BaiduTableModels.BaiduTableResponse(1, List.of(new BaiduTableModels.BaiduTableItem(
                List.of(), List.of(), List.of(new BaiduTableModels.BaiduTableCell(0, 1, 3, 4, "未提供得0分", List.of())), List.of())),
                null, null, 1L);
        var table = BaiduTableArtifactAdapter.toCanonicalTables(response, 10, "第七章 评分规则").getFirst();
        assertThat(table.isScoreTable()).isFalse();
        assertThat(table.sourceSection()).isEqualTo("第七章 评分规则");
    }
    private static final String[] HEADER = {"序号", "评价维度", "评分细则", "分值"};

    @Test void sameTableWithRepeatedHeaderKeepsSourcePagesAndRemovesDuplicateHeader() {
        var first = table("p9", 9, "评分规则", HEADER, new String[]{"1", "价格", "得60分", "60"}, new String[]{"2", "服务", "得40分", "40"});
        var next = table("p10", 10, "评分规则", HEADER, new String[]{"", "", "续页描述", ""}, new String[]{"3", "合计", "", "100"});
        var actual = BaiduCrossPageTableLinker.mergeCrossPageScoreTables(List.of(first, next), java.util.Map.of(9, first, 10, first));
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0).rows()).isEqualTo(5);
        assertThat(actual.get(0).cells()).filteredOn(c -> "合计".equals(c.rawText()))
                .singleElement().satisfies(c -> assertThat(c.sourcePage()).isEqualTo(10));
    }

    @Test void separateTablesWithRepeatedHeadersMustNotBeMerged() {
        var first = table("p9", 9, "评分规则", HEADER, new String[]{"1", "价格", "得60分", "60"}, new String[]{"2", "服务", "得40分", "40"});
        var closed = table("closed", 9, "评分规则", HEADER, new String[]{"1", "价格", "得60分", "60"}, new String[]{"2", "总分", "", "100"});
        var afterClosed = table("after", 10, "评分规则", HEADER, new String[]{"", "", "续页描述", ""}, new String[]{"3", "其他", "得10分", "10"});
        assertThat(BaiduCrossPageTableLinker.mergeCrossPageScoreTables(List.of(closed, afterClosed), java.util.Map.of(9, closed, 10, closed))).hasSize(2);
        for (var next : List.of(
                table("reset", 10, "评分规则", HEADER, new String[]{"", "", "续页描述", ""}, new String[]{"1", "能力", "得10分", "10"}),
                table("gap", 10, "评分规则", HEADER, new String[]{"", "", "续页描述", ""}, new String[]{"4", "能力", "得10分", "10"}),
                table("remote", 11, "评分规则", HEADER, new String[]{"", "", "续页描述", ""}, new String[]{"3", "能力", "得10分", "10"}),
                table("other", 10, "另一方案", HEADER, new String[]{"", "", "续页描述", ""}, new String[]{"3", "能力", "得10分", "10"}))) {
            assertThat(BaiduCrossPageTableLinker.mergeCrossPageScoreTables(List.of(first, next), java.util.Map.of(9, first, 10, first, 11, first))).hasSize(2);
        }
    }

    @Test void cannotSkipAnInterveningIndependentScoreTable() {
        var first = table("first", 9, "评分规则", HEADER, new String[]{"1", "价格", "得60分", "60"}, new String[]{"2", "服务", "得40分", "40"});
        var middle = table("middle", 9, "评分规则", HEADER, new String[]{"10", "其他", "得20分", "20"});
        var next = table("next", 10, "评分规则", HEADER, new String[]{"", "", "续页描述", ""}, new String[]{"3", "能力", "得10分", "10"});
        assertThat(BaiduCrossPageTableLinker.mergeCrossPageScoreTables(List.of(first, middle, next), java.util.Map.of(9, first, 10, first))).hasSize(3);
    }
    @Test void inferredOrDefaultSectionAloneCannotProveContinuation() {
        var first = table("first", 9, "评审办法/详细评审标准", HEADER, new String[]{"1", "价格", "得60分", "60"}, new String[]{"2", "服务", "得40分", "40"});
        var next = table("next", 10, "评审办法/详细评审标准", HEADER, new String[]{"", "", "续页描述", ""}, new String[]{"3", "能力", "得10分", "10"});
        assertThat(BaiduCrossPageTableLinker.mergeCrossPageScoreTables(List.of(first, next))).hasSize(2);
    }
    @Test void unclassifiedTailUsesPriorColumnRolesOnlyWithTrustedOwnership() {
        var first = table("first", 9, "评分规则", new String[]{"分项", "评分因素", "分值", "说明"},
                new String[]{"技术", "服务", "5", "首段规则"});
        var tail = table("tail", 10, "评分规则", new String[]{"", "", "", "未提供得0分"},
                new String[]{"技术", "配送", "5", "下一项规则"});
        tail = new CanonicalTable(tail.tableId(), tail.sourcePage(), tail.sourceSection(), tail.rows(), tail.columns(),
                tail.mergedRegions(), tail.cells(), false, "PDF");
        assertThat(BaiduCrossPageTableLinker.mergeCrossPageScoreTables(List.of(first, tail))).hasSize(2);
        var linked = BaiduCrossPageTableLinker.mergeCrossPageScoreTables(List.of(first, tail), java.util.Map.of(9, first, 10, first));
        assertThat(linked).hasSize(1);
        assertThat(linked.getFirst().cells()).filteredOn(c -> "未提供得0分".equals(c.rawText()))
                .singleElement().satisfies(c -> {
                    assertThat(c.logicalRowId()).isEqualTo("first-r1");
                    assertThat(c.sourcePage()).isEqualTo(10);
                });
        var independent = table("independent", 10, "评分规则", new String[]{"技术", "新项", "5", "新规则"});
        independent = new CanonicalTable(independent.tableId(), 10, independent.sourceSection(), independent.rows(), 4,
                independent.mergedRegions(), independent.cells(), false, "PDF");
        assertThat(BaiduCrossPageTableLinker.mergeCrossPageScoreTables(List.of(first, independent), java.util.Map.of(9, first, 10, first))).hasSize(2);
    }

    private static CanonicalTable table(String id, int page, String section, String[]... rows) {
        var cells = new ArrayList<CanonicalCellDto>();
        for (int row = 0; row < rows.length; row++) for (int col = 0; col < rows[row].length; col++) {
            String value = rows[row][col];
            cells.add(new CanonicalCellDto(id + "_r" + row + "_c" + col, row, col, value, value,
                    1, 1, false, "pdf:page=" + page + ";row=" + row + ";col=" + col, page, id + "-r" + row));
        }
        return new CanonicalTable(id, page, section, rows.length, rows[0].length, List.of(), cells, true, "PDF");
    }
}
