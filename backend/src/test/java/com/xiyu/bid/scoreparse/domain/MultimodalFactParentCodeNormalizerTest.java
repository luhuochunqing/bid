package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MultimodalFactParentCodeNormalizerTest {

    @Test
    void normalizesChapterPrefixedParentCodeToCanonicalParentCode() {
        var parent = fact("business", "");
        var child = fact("1", "3.2.3（1）商务评分标准/business");

        var normalized = MultimodalFactParentCodeNormalizer.normalize(List.of(parent, child));

        assertThat(normalized.get(1).parentCode()).isEqualTo("business");
    }

    @Test
    void leavesUnresolvedParentCodeUntouchedSoValidatorKeepsReviewRequired() {
        var child = fact("1", "not-present");

        var normalized = MultimodalFactParentCodeNormalizer.normalize(List.of(child));

        assertThat(normalized.get(0).parentCode()).isEqualTo("not-present");
    }

    @Test
    void leavesAmbiguousSameSectionParentCodeUntouched() {
        var parentA = fact("business", "");
        var parentB = fact("business", "");
        var child = fact("1", "章节标题/business");

        assertThat(MultimodalFactParentCodeNormalizer.normalize(List.of(parentA, parentB, child)).get(2).parentCode())
                .isEqualTo("章节标题/business");
    }

    @Test
    void preservesEmptyInputNullEntryAndUnrelatedFactIdentity() {
        assertThat(MultimodalFactParentCodeNormalizer.normalize(List.of())).isEmpty();
        var fact = fact("1", "");
        var normalized = MultimodalFactParentCodeNormalizer.normalize(Arrays.asList(null, fact));

        assertThat(normalized).containsExactly(null, fact);
    }

    private MultimodalScoreFact fact(String code, String parentCode) {
        return new MultimodalScoreFact("3.2.3（1）商务评分标准", code, parentCode, "商务",
                BigDecimal.TEN, "最高得10分", "OBJECTIVE", 42, "p42-t0016",
                List.of("p42-t0016-r1-c1"), "最高得10分", 0.9);
    }
}
