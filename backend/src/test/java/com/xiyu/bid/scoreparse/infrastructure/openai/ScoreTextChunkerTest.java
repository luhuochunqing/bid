package com.xiyu.bid.scoreparse.infrastructure.openai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreTextChunkerTest {
    @Test
    void chunksCoverEveryCharacterWithBoundedSizeAndOverlap() {
        String source = ("第一条评分规则。\n第二条评分规则。\n").repeat(20);

        var chunks = ScoreTextChunker.split(source, 80, 12);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text().length()).isLessThanOrEqualTo(80));
        assertThat(chunks.get(0).start()).isZero();
        assertThat(chunks.get(chunks.size() - 1).end()).isEqualTo(source.length());
        for (int i = 1; i < chunks.size(); i++) {
            assertThat(chunks.get(i).start()).isLessThan(chunks.get(i - 1).end());
            assertThat(chunks.get(i).start()).isGreaterThan(chunks.get(i - 1).start());
        }
    }

    @Test
    void neverSplitsSurrogatePair() {
        String source = "a".repeat(79) + "😀" + "b".repeat(20);

        var chunks = ScoreTextChunker.split(source, 80, 8);

        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(Character.isHighSurrogate(chunk.text().charAt(chunk.text().length() - 1))).isFalse();
            assertThat(Character.isLowSurrogate(chunk.text().charAt(0))).isFalse();
        });
        assertThat(chunks.stream().map(ScoreTextChunker.Chunk::text).reduce("", String::concat))
                .contains("😀");
    }
}
