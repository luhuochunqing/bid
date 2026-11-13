package com.xiyu.bid.scoreparse.domain;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ScoringSourceRefTest {
    @Test void wordUnitKeepsPartAnchorAndRelationshipDistinct() {
        var ref=ScoringSourceRef.from(Map.of("partName","word/document.xml","anchor","p1","relationshipId","rId2"));
        assertThat(ref.marker()).isEqualTo("[word/document.xml#p1 rId2]");
        assertThat(ref.sameUnit(ScoringSourceRef.from(Map.of("partName","word/header1.xml","anchor","p1","relationshipId","rId2")))).isFalse();
        assertThat(ref.sameUnit(ScoringSourceRef.from(Map.of("partName","word/document.xml","anchor","p10","relationshipId","rId2")))).isFalse();
    }
    @Test void numericPageIdentitySurvivesJsonNumberWidth() {
        var ref=ScoringSourceRef.from(Map.of("pageNumber",17));
        assertThat(ref.marker()).isEqualTo("[第 17 页]");
        assertThat(ref.sameUnit(ScoringSourceRef.from(Map.of("pageNumber",17L)))).isTrue();
    }
}
