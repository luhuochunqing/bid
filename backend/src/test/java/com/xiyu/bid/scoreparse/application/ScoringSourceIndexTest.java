package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoringDocument;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ScoringSourceIndexTest {
    @Test void repeatedTextUsesSelectedOffsetInsteadOfRejectingAmbiguity() {
        String text="[第 1 页]\nSAME CERTIFICATE\n\n[第 2 页]\nSAME CERTIFICATE";
        var index=new ScoringSourceIndex(new ScoringDocument(text,List.of(
                Map.of("pageNumber",1),Map.of("pageNumber",2)),null));
        String selected="SAME CERTIFICATE";
        var excerpt=new ScoreDocExcerptExtractor.Excerpt(selected,List.of(
                new ScoreDocExcerptExtractor.Segment("second",selected,text.lastIndexOf(selected))));
        assertThat(index.select(excerpt)).extracting(ref->ref.pageNumber()).containsExactly(2);
        var wrong=new ScoreDocExcerptExtractor.Excerpt(selected,List.of(
                new ScoreDocExcerptExtractor.Segment("forged",selected,0)));
        assertThat(index.select(wrong)).isEmpty();
    }
}
