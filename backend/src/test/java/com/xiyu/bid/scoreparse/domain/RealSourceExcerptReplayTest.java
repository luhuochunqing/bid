package com.xiyu.bid.scoreparse.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Raw page/cell fragments captured from the real PDF, independently of expected score outcomes. */
class RealSourceExcerptReplayTest {
    @Test void realCrossPageCellsKeepOffsetsAndRowAnchorsAcrossNamedScoringPages() throws Exception {
        var resource = getClass().getResourceAsStream("/scoreparse/source-evidence-real-excerpts.json");
        var artifact = new ObjectMapper().readValue(resource, DocumentArtifact.class);
        var anchors = SourceAnchorCatalog.build(artifact);
        assertThat(anchors).hasSize(4);
        assertThat(anchors.stream().filter(a -> a.sourceSpans().stream().map(SourceSpan::pageNumber).distinct().count() == 2))
                .hasSize(3);
        for (var anchor : anchors) {
            assertThat(anchor.sourceSpans()).allSatisfy(span -> {
                assertThat(span.endOffset()).isEqualTo(span.rawText().length());
                assertThat(span.startOffset()).isZero();
                assertThat(span.locator()).startsWith("pdf:page=");
                assertThat(span.fileSha256()).isBlank();
            });
        }
    }
}
