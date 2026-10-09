package com.xiyu.bid.scoreparse.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class Project1359ScoringContractTest {
    @Test void real1359MaximumColumnsDoNotConflictWithMinimumTotals() throws Exception {
        var context = realContext();
        assertThat(context.problems()).isEmpty();
        assertThat(context.subtotals().get("t-0002").value()).isEqualByComparingTo("100");
        assertThat(context.subtotals().get("t-0003").value()).isEqualByComparingTo("100");
    }

    @Test void reversedMinimumMaximumColumnsStillUseMaximum() {
        var context = context(List.of("评审项目", "评审内容", "最高分", "最低分"),
                List.of("合计", "", "100", "38"));
        assertThat(context.problems()).isEmpty();
        assertThat(context.subtotals().get("t-test").value()).isEqualByComparingTo("100");
    }

    @Test void unidentifiedMultipleNumbersAreNotGuessedAsMaximum() {
        var context = context(List.of("评审项目", "评审内容", "数值甲", "数值乙"),
                List.of("合计", "", "12", "100"));
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void explicitTotalsInUnidentifiedColumnsCannotDisappear() {
        var context = context(List.of("评审项目", "评审内容", "数值甲", "数值乙"),
                List.of("合计", "", "总分100分", "满分90分"));
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void emptyMaximumCellDoesNotSilentlyDiscardTableSubtotal() {
        var context = context(List.of("评审项目", "评审内容", "最低分", "最高分"),
                List.of("合计", "", "38", ""));
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void singleNumberUnderUnknownHeaderIsNotColumnEvidence() {
        var context = context(List.of("评审项目", "评审内容", "数值"), List.of("合计", "", "100"));
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void uniqueExplicitMaximumUnderUnknownHeaderIdentifiesSubtotalColumn() {
        var context = context(List.of("序号", "评分因素", "评分标准"),
                List.of("合计得分", "满分（100分）", ""));
        assertThat(context.problems()).isEmpty();
        assertThat(context.subtotals().get("t-test").value()).isEqualByComparingTo("100");
    }

    @Test void singleItemMaximumMixedWithTotalDescriptionDoesNotHijackSubtotalColumn() {
        var context = context(List.of("序号", "评分因素", "评分标准"),
                List.of("合计", "本表单项满分10分，共10项，合计100分", ""));
        // 只有单项满分、缺乏整表满分证据：必须保持未决，绝不能产出单项分值作为小计
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void singleItemMaximumWithSpaceDoesNotHijackSubtotalColumn() {
        var context = context(List.of("序号", "评分因素", "评分标准"),
                List.of("合计", "本表单项 满分10分，共10项，合计100分", ""));
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void perItemHighestMaximumDoesNotHijackSubtotalColumn() {
        var context = context(List.of("序号", "评分因素", "评分标准"),
                List.of("合计", "本表每项最高满分10分，共10项，合计100分", ""));
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void perItemMaximumWithParticleDoesNotHijackSubtotalColumn() {
        var context = context(List.of("序号", "评分因素", "评分标准"),
                List.of("合计", "本表每项的满分10分，共10项，合计100分", ""));
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void perItemMaximumWithYiVariantDoesNotHijackSubtotalColumn() {
        var context = context(List.of("序号", "评分因素", "评分标准"),
                List.of("合计", "每一项满分10分，共10项，合计100分", ""));
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void perArticleMaximumDoesNotHijackSubtotalColumn() {
        var context = context(List.of("序号", "评分因素", "评分标准"),
                List.of("合计", "每一条满分10分，共10项，合计100分", ""));
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void wholeProjectMaximumIsStillRecognizedAsTableTotal() {
        var context = context(List.of("序号", "评分因素", "评分标准"),
                List.of("合计", "本项目满分100分", ""));
        // "本项目"是整表主体而非单项：不得被单项规则抑制，整表总量 100 正常决议
        assertThat(context.problems()).isEmpty();
        assertThat(context.total().value()).isEqualByComparingTo("100");
    }

    @Test void mixedPerItemAndTableTotalResolvesTableTotalWithoutHijack() {
        var context = context(List.of("序号", "评分因素", "评分标准"),
                List.of("合计", "每项 满分10分，满分100分", ""));
        // 同格出现单项句与真总量句：干净的满分 100 独立成证，决议 100；单项 10 不得进入任何位置
        assertThat(context.problems()).isEmpty();
        assertThat(context.total().value()).isEqualByComparingTo("100");
        assertThat(context.subtotals().values()).allSatisfy(
                subtotal -> assertThat(subtotal.value()).isNotEqualByComparingTo("10"));
    }

    @Test void mixedPerItemAndZongfenTotalStaysUnresolved() {
        var context = context(List.of("序号", "评分因素", "评分标准"),
                List.of("合计", "每项 满分10分，总分100分", ""));
        // 边界：总分声明不能确定小计列（既有契约），保持未决；整表总量仍由正文总分决议为 100
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
        assertThat(context.total().value()).isEqualByComparingTo("100");
    }

    @Test void headerlessPerItemMaximumRemainsUnresolved() {
        var table = table("t-hl", "商务评审细则", List.of(List.of("合计", "每一项满分10分")));
        var summary = List.of(
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-hl-r0-c0", 0, 0, "合计", "合计", 1, 1, false,
                        "fixture:t-hl"),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-hl-r0-c1", 0, 1, "每一项满分10分",
                        "每一项满分10分", 1, 1, false, "fixture:t-hl"));
        // 无表头 legacy 路径同样适用单项语义：单项 10 不得成为小计证据
        var selection = ScoreSubtotalColumnPolicy.select(table, 0, summary);
        assertThat(selection.unresolved()).isTrue();
        assertThat(selection.problem("t-hl").code()).isEqualTo("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void perItemMaximumInProseDoesNotConflictWithTableTotal() {
        var table = table("t-test", "本项目评分办法 / 技术评审细则",
                List.of(List.of("序号", "评分因素", "评分标准"),
                        List.of("合计", "满分100分", "")));
        var context = OriginalScoringContext.resolve(artifact(List.of(table)), "# 本项目评分办法\n每项 满分10分，总分100分");
        // 正文单项句不是整表总量：不得与表格满分 100 冲突；总分仍识别 100
        assertThat(context.problems())
                .noneMatch(p -> p.code().equals("ORIGINAL_TOTAL_CONFLICT") || p.code().equals("TABLE_SUBTOTAL_CONFLICT"));
        assertThat(context.total().value()).isEqualByComparingTo("100");
    }

    @Test void documentTotalFallbackIgnoresPerItemStatement() {
        // 无表格、无正文规则可命中时，DOCUMENT_TOTAL 兜底也不得把"每项总分10分"当作项目总分
        var context = OriginalScoringContext.resolve(artifact(List.of()), "# 本项目评分办法\n本项目每项总分10分\n");
        assertThat(context.total().value()).isNull();
        assertThat(context.problems()).noneMatch(p -> p.code().equals("ORIGINAL_TOTAL_CONFLICT"));
    }

    @Test void documentTotalFallbackOverCommaSeparatedPerItemStillResolvesTotal() {
        // 逗号后的干净总分分句必须独立成证：兜底查「总分」所在分句，不得被前半句的单项量词毒掉
        var context = OriginalScoringContext.resolve(artifact(List.of()),
                "# 本项目评分办法\n| 评分办法：每项10分，总分100分 |\n");
        assertThat(context.total().value()).isEqualByComparingTo("100");
    }

    @Test void documentTotalFallbackKeepsLineBoundaryBetweenClauses() {
        // 跨行窗口 pin：上一行的单项量词不得毒掉下一行的干净分句（行边界按分句处理）。
        // 注：此行为在 R6 之前的实现（join 用空格）下同样成立，故非 R6 RED-GREEN 对，
        // 而是防止后续把 join 改回空格时回归的固化用例。
        var context = OriginalScoringContext.resolve(artifact(List.of()),
                "前言段落。\n每项10分\n总分100分\n");
        assertThat(context.total().value()).isEqualByComparingTo("100");
    }

    @Test void explicitMaximumHeaderWithPerItemSummaryStaysUnresolved() {
        var context = context(List.of("评审项目", "评审内容", "最高分"),
                List.of("合计", "", "每项 满分10分"));
        // 明确最高分列下，合计格只有单项说明：不得被判为已验证，须保持未决
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }
    @Test void headerlessExplicitMaximumStillResolves() {
        var table = table("t-hl", "商务评审细则", List.of(List.of("合计", "满分（100分）")));
        var summary = List.of(
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-hl-r0-c0", 0, 0, "合计", "合计", 1, 1, false,
                        "fixture:t-hl"),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-hl-r0-c1", 0, 1, "满分（100分）", "满分（100分）",
                        1, 1, false, "fixture:t-hl"));
        // 对照：干净的整表满分声明在 legacy 路径仍可决议（防过度抑制）
        var selection = ScoreSubtotalColumnPolicy.select(table, 0, summary);
        assertThat(selection.unresolved()).isFalse();
        assertThat(selection.column()).isEqualTo(1);
    }

    @Test void conflictingDeclarationsInSameCellRemainUnresolved() {
        var context = context(List.of("序号", "评分因素", "评分标准"),
                List.of("合计", "满分100分，总分90分", ""));
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void explicitMaximumHeaderTakesPrecedenceOverOtherNumericColumns() {
        var context = context(List.of("序号", "评分因素", "其他数值", "最高分"),
                List.of("合计", "说明", "50", "100"));
        assertThat(context.problems()).isEmpty();
        assertThat(context.subtotals().get("t-test").value()).isEqualByComparingTo("100");
    }

    @Test void multipleUnidentifiedNumbersRemainUnresolved() {
        var context = context(List.of("序号", "评分因素", "数值A", "数值B"),
                List.of("合计", "说明", "50", "100"));
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }


    @Test void precedingBannerDoesNotHijackRealScoreHeader() {
        var table = table("t-test", "本项目评分办法 / 技术评审细则",
                List.of(List.of("满分", "", "", ""), List.of("序号", "评审项目", "评审内容", "分值"),
                        List.of("1", "方案", "方案完整得100分", "100"), List.of("合计", "", "", "100")));
        var context = OriginalScoringContext.resolve(artifact(List.of(table)), "# 本项目评分办法\n总分100分。");
        assertThat(context.problems()).isEmpty();
        assertThat(context.subtotals().get("t-test").value()).isEqualByComparingTo("100");
    }

    @Test void onlyMinimumColumnCannotBecomeMaximum() {
        var context = context(List.of("评审项目", "评审内容", "最低分"), List.of("合计", "", "38"));
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void explicitTotalWordingInMinimumCellCannotOverrideMaximumColumn() {
        var context = context(List.of("评审项目", "评审内容", "最低分", "最高分"),
                List.of("合计", "", "总分12分", "100"));
        assertThat(context.problems()).isEmpty();
        assertThat(context.subtotals().get("t-test").value()).isEqualByComparingTo("100");
    }

    @Test void parenthesizedExplicitMaximumUsesTheSameGrammarAsContext() {
        var context = context(List.of("评审项目", "评审内容", "满分"), List.of("合计", "", "满分（100分）"));
        assertThat(context.subtotals().get("t-test").value()).isEqualByComparingTo("100");
        assertThat(context.problems()).isEmpty();
    }

    @Test void explicitMaximumWithWeightAnnotationIsNotUnknown() {
        var context = context(List.of("评审项目", "评审内容", "最高分"), List.of("合计", "", "满分100分（权重20%）"));
        assertThat(context.subtotals().get("t-test").value()).isEqualByComparingTo("100");
        assertThat(context.problems()).isEmpty();
    }

    @Test void explicitMaximumAndScoreHeaderAliasesRemainCompatible() {
        for (String header : List.of("最高分数", "最高分值", "最高得分", "满分（分）", "评分分值")) {
            var context = context(List.of("评审项目", "评审内容", header), List.of("合计", "", "100"));
            assertThat(context.problems()).as(header).isEmpty();
            assertThat(context.subtotals().get("t-test").value()).isEqualByComparingTo("100");
        }
    }

    @Test void singleExplicitScoreColumnRemainsCompatible() {
        var context = context(List.of("评审项目", "评审内容", "分值"), List.of("合计", "", "100"));
        assertThat(context.subtotals().get("t-test").value()).isEqualByComparingTo("100");
        assertThat(context.problems()).isEmpty();
    }

    @Test void differentTotalsInSameMaximumColumnStillConflict() {
        var headers = List.of("评审项目", "评审内容", "最低分", "最高分");
        var table = table("t-test", "本项目评分办法 / 技术评审细则",
                List.of(headers, List.of("方案", "方案完整得100分", "0", "100"),
                        List.of("合计", "", "38", "100"), List.of("总计", "", "38", "90")));
        var context = OriginalScoringContext.resolve(artifact(List.of(table)), "# 本项目评分办法\n总分100分。");
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_CONFLICT");
    }

    public static OriginalScoringContext realContext() throws Exception {
        JsonNode raw;
        String text;
        try (var in = Project1359ScoringContractTest.class.getResourceAsStream("/scoreparse-golden/1359_tables_v1.json")) {
            raw = new ObjectMapper().readTree(in);
        }
        try (var in = Project1359ScoringContractTest.class.getResourceAsStream("/scoreparse-golden/1359_sidecar.md")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<DocumentArtifact.CanonicalTable> tables = new ArrayList<>();
        for (int index : List.of(2, 3)) {
            var rows = new ArrayList<List<String>>();
            for (JsonNode row : raw.path("tables").get(index).path("rows")) {
                var values = new ArrayList<String>();
                for (JsonNode cell : row) values.add(cell.path("text").asText());
                rows.add(values);
            }
            tables.add(table("t-000" + index, index == 2 ? "商务评审细则" : "技术评审细则", rows));
        }
        return OriginalScoringContext.resolve(artifact(tables), text);
    }

    private static OriginalScoringContext context(List<String> headers, List<String> summary) {
        var row = new ArrayList<String>();
        row.add("方案"); row.add("方案完整得100分");
        while (row.size() < headers.size()) row.add("0");
        row.set(headers.size() - 1, "100");
        return OriginalScoringContext.resolve(artifact(List.of(table("t-test",
                "本项目评分办法 / 技术评审细则", List.of(headers, row, summary)))), "# 本项目评分办法\n总分100分。");
    }

    private static DocumentArtifact artifact(List<DocumentArtifact.CanonicalTable> tables) {
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("1359", "fixture-1359", "DOCX", "2.2"), tables, List.of());
    }

    private static DocumentArtifact.CanonicalTable table(String id, String section, List<List<String>> rows) {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        for (int r = 0; r < rows.size(); r++) for (int c = 0; c < rows.get(r).size(); c++) {
            String text = rows.get(r).get(c);
            cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(id + "-r" + r + "-c" + c,
                    r, c, text, text, 1, 1, false, "fixture:" + id));
        }
        return new DocumentArtifact.CanonicalTable(id, 1, section, rows.size(), rows.get(0).size(), List.of(), cells, true);
    }
}
