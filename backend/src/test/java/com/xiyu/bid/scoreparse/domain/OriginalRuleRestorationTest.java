package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OriginalRuleRestorationTest {

    @Test
    @DisplayName("非表格正文段落事实（text:line）带有 sourceSpans 时，应穿透还原规则文本")
    void shouldRestoreRuleTextFromTextParagraphSourceSpans() {
        String locator = "text:line[693]";
        String rawText = "最终评审折扣率等于评标基准价，得 30 分；每高于评标基准价 1%扣 0.25 分，每低于评标基准价 1%扣 0.5 分，最低得 20 分。";
        String quote = "最终评审折扣率等于评标基准价，得 30 分；";

        var span = new SourceSpan("doc-1", "hash-1", "TEXT", "scheme-1", 21,
                "", locator, 100, 100 + rawText.length(), rawText, locator);
        var claim = new ModelSourceClaim("第三章 评审标准", 21, "", List.of(locator), "scheme-1", "IN_SCHEME");
        var metadata = new ScoreSourceMetadata(rawText, quote, List.of(span), List.of(), "价格", "OBJECTIVE", claim);

        var fact = new MultimodalScoreFact("第三章 评审标准", "shangwu_baojia_defen", null, "价格",
                new BigDecimal("30.00"), rawText, "OBJECTIVE", 21, "", List.of(locator), quote,
                1.0, ChildAggregation.UNKNOWN, "scheme-1", "IN_SCHEME", metadata);

        var artifact = new DocumentArtifact(new ArtifactMeta("doc.pdf", "hash", "PDF", "2.2"), List.of(), List.of());

        var result = OriginalRuleRestoration.restore(fact, artifact, List.of(fact));

        assertThat(result.text()).isNotBlank();
        assertThat(result.text()).contains("最终评审折扣率等于评标基准价，得 30 分");
        assertThat(result.spans()).isNotEmpty();
        assertThat(result.spans().get(0).locator()).isEqualTo(locator);
    }
}
