package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextPrimaryEvidenceBinderTest {
    @Test
    void bindsWhitespaceVariantQuoteToOriginalTextLocator() {
        var fact = new MultimodalScoreFact("评分办法", "1", "", "业绩", BigDecimal.TEN,
                "满足条件得10分", "OBJECTIVE", null, "", List.of(),
                "满足条件得 10 分", 0.9, ChildAggregation.SUM);
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), null,
                "业绩：满足条件得\n10\u00a0分。\n");

        assertThat(bound).hasSize(1);
        assertThat(bound.get(0).sourceCellIds()).containsExactly("text:line[0]");
        assertThat(bound.get(0).quotedText()).isEqualTo("满足条件得 10 分");
    }

    @Test
    void locatesTheWholeQuoteWhenItsFirstTokenAppearsEarlier() {
        var fact = new MultimodalScoreFact("评分办法", "1", "", "业绩", BigDecimal.TEN,
                "满足条件得10分", "OBJECTIVE", null, "", List.of(),
                "满足条件得 10 分", 0.9, ChildAggregation.SUM);
        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), null,
                "满足条件得。\n评分要求：满足条件得\n10\u00a0分。\n");

        assertThat(bound.get(0).sourceCellIds()).containsExactly("text:line[1]");
    }

    @Test
    void rejectsWhitespaceOnlyQuote() {
        var fact = new MultimodalScoreFact("评分办法", "1", "", "业绩", BigDecimal.TEN,
                "满足条件得10分", "OBJECTIVE", null, "", List.of(), "\u00a0", 0.9, ChildAggregation.SUM);

        assertThat(TextPrimaryEvidenceBinder.bind(List.of(fact), null, "评分要求：10分").get(0).sourceCellIds())
                .isEmpty();
    }

    @Test
    void tableBindingAlsoAllowsWhitespaceVariantQuote() {
        var cell = new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                "t-r1-c1", 1, 1, "满足条件得\n10\u00a0分", "满足条件得 10 分", 1, 1,
                false, "word/document.xml", 3);
        var table = new DocumentArtifact.CanonicalTable("t-1", 3, "评分办法", 2, 2,
                List.of(), List.of(cell), true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "docx", "2.0"),
                List.of(table), List.of());
        var fact = new MultimodalScoreFact("评分办法", "1", "", "业绩", BigDecimal.TEN,
                "满足条件得10分", "OBJECTIVE", 3, "", List.of(), "满足条件得 10 分", 0.9);

        var bound = TextPrimaryEvidenceBinder.bind(List.of(fact), artifact, "");

        assertThat(bound.get(0).tableId()).isEqualTo("t-1");
        assertThat(bound.get(0).sourceCellIds()).containsExactly("t-r1-c1");
    }
}
