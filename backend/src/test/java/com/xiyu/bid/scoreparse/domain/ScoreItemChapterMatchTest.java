package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreItemChapterMatchTest {

    @Test
    void quoteContainingTitleIsRelated() {
        assertThat(ScoreItemChapterMatch.related("商务", "报价", "见商务部分", null, List.of("商务"))).isTrue();
    }

    @Test
    void uncertainDefaultsToRelated() {
        assertThat(ScoreItemChapterMatch.related("其他", "无关描述", null, null, List.of("技术方案"))).isTrue();
        assertThat(ScoreItemChapterMatch.related("技术", "方案", null, null, List.of())).isTrue();
        assertThat(ScoreItemChapterMatch.related("商务", "报价", "见商务部分", null, List.of("技术方案"))).isFalse();
    }
}
