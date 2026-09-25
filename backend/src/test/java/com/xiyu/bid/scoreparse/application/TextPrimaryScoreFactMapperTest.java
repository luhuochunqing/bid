package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextPrimaryScoreFactMapperTest {

    @Test
    void bounds_fact_id_for_long_section_titles_and_preserves_parent_link() {
        String sectionTitle = "技术评审部分".repeat(20);
        MultimodalScoreFact parent = fact(sectionTitle, "1", "", 10);
        MultimodalScoreFact child = fact(sectionTitle, "1.1", "1", 10);
        var validation = new MultimodalScoreFactValidator()
                .validate(List.of(parent, child), null, BigDecimal.TEN);

        var mappedParent = TextPrimaryScoreFactMapper.map(parent, null, validation);
        var mappedChild = TextPrimaryScoreFactMapper.map(child, null, validation);

        assertThat(mappedParent.factId()).hasSizeLessThanOrEqualTo(64);
        assertThat(mappedChild.factId()).hasSizeLessThanOrEqualTo(64);
        assertThat(mappedChild.parentFactId()).isEqualTo(mappedParent.factId());
    }

    @Test
    void preserves_parent_link_when_source_paragraph_and_detail_tables_use_different_sections() {
        String schemeId = com.xiyu.bid.scoreparse.domain.OriginalScoringContext.schemeId("第三章 评审标准");
        MultimodalScoreFact parent = fact("评审标准", "BUSINESS", "", 40)
                .withScope(schemeId, "IN_SCHEME");
        MultimodalScoreFact child = fact("商务评分表", "B1", "BUSINESS", 40)
                .withScope(schemeId, "IN_SCHEME");
        var validation = new MultimodalScoreFactValidator()
                .validate(List.of(parent, child), null, BigDecimal.valueOf(40));

        var mappedParent = TextPrimaryScoreFactMapper.map(parent, null, validation);
        var mappedChild = TextPrimaryScoreFactMapper.map(child, null, validation);

        assertThat(mappedChild.parentFactId()).isEqualTo(mappedParent.factId());
    }

    private static MultimodalScoreFact fact(String sectionTitle, String code, String parentCode, int weight) {
        return new MultimodalScoreFact(sectionTitle, code, parentCode, "技术方案",
                BigDecimal.valueOf(weight), "满足要求得" + weight + "分", "OBJECTIVE", 1,
                "table-1", List.of("cell-" + code), "满足要求", 1.0);
    }
}
