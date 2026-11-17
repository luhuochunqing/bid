// Input: synthetic Markdown source and expected scoring contracts.
// Output: executed source total, alias identity and complete-rule assertions.
package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalRuleRestoration;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

public final class SourceClosureGoldenReplay {
    private static final String SECTION = "第四章：评审方法";
    private SourceClosureGoldenReplay() {}
    public static void verify(String source, JsonNode expected) {
        var tables = new ArrayList<CanonicalTable>();
        var facts = new ArrayList<MultimodalScoreFact>();
        int index = 0;
        for (var line : source.lines().filter(l -> l.startsWith("| ") && l.contains("评分（")).toList()) {
            var parts = Arrays.stream(line.split("\\|", -1)).map(String::strip).toList();
            int page = ++index;
            String id = "page" + page;
            var cells = new ArrayList<CanonicalCellDto>();
            int row = page == 1 ? 1 : 0;
            if (page == 1) for (int col = 0; col < 4; col++) cells.add(cell(id, 0, col,
                    List.of("评审项目", "评审项目", "分值", "评分标准").get(col), page));
            for (int col = 0; col < 4; col++) cells.add(cell(id, row, col, parts.get(col + 1), page));
            tables.add(new CanonicalTable(id, page, SECTION, row + 1, 4,
                    List.of(), cells, page == 1, "PDF"));
            String code = "item" + page;
            var f = new MultimodalScoreFact(SECTION, code, "", parts.get(2),
                    new BigDecimal(parts.get(3)), "模型摘要", "SUBJECTIVE", page, id, List.of(id + "r" + row + "c3"),
                    parts.get(4), .9, ChildAggregation.UNKNOWN, OriginalScoringContext.schemeId(SECTION), "IN_SCHEME");
            facts.add(f);
            if (page == 1) facts.add(new MultimodalScoreFact(f.sectionTitle(), "alias", "", f.dimension(),
                    f.weight(), "不同模型摘要", f.scoreType(), page, id, f.sourceCellIds(), f.quotedText(), .9,
                    f.childAggregation(), f.schemeId(), f.scopeStatus()));
        }
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "synthetic-sha", "PDF", "2.2"), tables, List.of());
        var context = OriginalScoringContext.resolve(artifact, source);
        assertThat(context.total().value()).isEqualByComparingTo(expected.path("declaredTotal").decimalValue());
        assertThat(context.total().source()).isEqualTo(expected.path("totalSource").asText());
        var normalized = MultimodalExtractionReviewPolicy.normalized(new MultimodalScoreFactExtractor.ExtractionResult(
                null, facts, "synthetic", 0), context, artifact, source).facts();
        assertThat(normalized).hasSize(expected.path("expectedFactCount").asInt());
        assertThat(normalized.stream().map(MultimodalScoreFact::weight).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(context.total().value());
        for (var item : expected.path("items")) {
            var actual = normalized.stream().filter(f -> f.dimension().equals(item.path("dimension").asText())).findFirst().orElseThrow();
            assertThat(actual.weight()).isEqualByComparingTo(item.path("weight").decimalValue());
            assertThat(OriginalRuleRestoration.restore(actual, artifact, normalized).text()).contains(item.path("detail").asText());
        }
    }
    private static CanonicalCellDto cell(String id, int row, int col, String raw, int page) {
        return new CanonicalCellDto(id + "r" + row + "c" + col, row, col, raw, raw, 1, 1, false,
                "pdf:page=" + page, page, id + "r" + row);
    }
}
