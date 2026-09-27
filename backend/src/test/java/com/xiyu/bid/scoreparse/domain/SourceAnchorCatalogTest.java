package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SourceAnchorCatalogTest {
    @Test void validAnchorRestoresRawEvenWhenModelMisspellsQuoteAndKeepsBothVersions() {
        var catalog = SourceAnchorCatalog.build(artifact());
        assertThat(catalog).hasSize(1);
        var anchor = catalog.getFirst();
        var input = fact(List.of(anchor.id()), "物流服物得8分");
        var restored = SourceAnchorCatalog.restore(input, catalog);
        assertThat(restored.quotedText()).isEqualTo("物流方案（8分）物流服\n务得8分");
        assertThat(restored.sourceCellIds()).containsExactly("name", "rule");
        assertThat(restored.sourceMetadata().modelQuotedText()).isEqualTo(input.quotedText());
        assertThat(restored.sourceMetadata().modelRuleText()).isEqualTo("模型概括");
        assertThat(restored.sourceMetadata().sourceSpans()).hasSize(2);
        assertThat(restored.sourceMetadata().sourceSpans().get(1).fileSha256()).isEqualTo("abc");
    }
    @Test void unknownAnchorAndOldMisspelledQuoteStayUnverified() {
        var old = fact(List.of(), "物流服物得8分");
        assertThat(SourceAnchorCatalog.restore(old, SourceAnchorCatalog.build(artifact()))).isEqualTo(old);
        var invalid = SourceAnchorCatalog.restore(fact(List.of("anchor:unknown"), "错误"), SourceAnchorCatalog.build(artifact()));
        assertThat(invalid.quotedText()).isEqualTo("错误");
        assertThat(invalid.sourceMetadata().diagnostics()).contains("SOURCE_ANCHOR_INVALID");
    }
    private static MultimodalScoreFact fact(List<String> ids, String quote) {
        return new MultimodalScoreFact("第三章 评审办法", "物流方案", "", "物流方案", BigDecimal.valueOf(8),
                "模型概括", "SUBJECTIVE", 9, "", ids, quote, .9, ChildAggregation.SUM);
    }
    private static DocumentArtifact artifact() {
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "abc", "PDF", "v2"),
                List.of(new DocumentArtifact.CanonicalTable("t", 9, "第三章 评审办法", 1, 2, List.of(), List.of(
                    new DocumentArtifact.CanonicalTable.CanonicalCellDto("name",0,0,"物流方案（8分）","",1,1,false,"pdf:page=9;col=0",9,"row"),
                    new DocumentArtifact.CanonicalTable.CanonicalCellDto("rule",0,1,"物流服\n务得8分","",1,1,false,"pdf:page=9;col=1",9,"row")),true)),List.of());
    }
}
