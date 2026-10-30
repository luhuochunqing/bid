package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PrimaryScoreSnapshotTest {
    @Test void preservesOriginalTextRangeAndActivationSchemeWithoutGrid() {
        String text = "标题\n规则满分100分\n结束";
        var total = new OriginalScoreEvidencePolicy.Total(new BigDecimal("100"), "TENDER_TEXT", "", "text", List.of());
        var snapshot = PrimaryScoreSnapshot.from("task", "synthetic.pdf", text, candidates(), total, "boundary");
        assertThat(snapshot.schemes()).hasSize(1);
        assertThat(snapshot.schemes().getFirst().computableCount()).isEqualTo(1);
        assertThat(snapshot.schemes().getFirst().computableTotal()).isEqualByComparingTo("100");
        var item = snapshot.items().getFirst();
        assertThat(item.schemeId()).isNotBlank();
        assertThat(item.computable()).isTrue();
        var span = item.fact().sourceMetadata().sourceSpans().getFirst();
        assertThat(text.substring(span.startOffset(), span.endOffset())).isEqualTo("规则满分100分");
        assertThat(span.rawText()).isEqualTo("规则满分100分");
        assertThat(item.fieldSources().get("ruleText")).isEqualTo("SOURCE");
    }
    @Test void assumedOneHundredIsNotRecordedAsDeclaredSourceTotal() {
        var total = new OriginalScoreEvidencePolicy.Total(null, "UNKNOWN", "", "", List.of());
        var snapshot = PrimaryScoreSnapshot.from("task", "synthetic.pdf", "规则满分100分", candidates(), total, "boundary");
        assertThat(snapshot.schemes().getFirst().declaredTotal()).isNull();
        assertThat(snapshot.schemes().getFirst().declaredTotalStatus()).isEqualTo("UNKNOWN");
        assertThat(snapshot.runDiagnostics()).containsEntry("totalSource", "DEFAULT_100_CONTRACT");
    }
    private static List<ScoreCandidate> candidates() {
        return List.of(new ScoreCandidate("1", "评分", "规则满分100分", new BigDecimal("100"),
                "OBJECTIVE", null, "规则满分100分", "text", "DUAL_TRACK_PRIMARY"));
    }
}
