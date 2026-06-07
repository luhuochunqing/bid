package com.xiyu.bid.biddraftagent.infrastructure.openai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenderDocumentTextChunkerTest {

    @Test
    void split_shouldKeepFullLongDocumentInsteadOfTruncatingTail() {
        String text = "A".repeat(24_000) + "TAIL_REQUIREMENT";

        List<String> chunks = TenderDocumentTextChunker.split(text, 10_000, 500);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allMatch(chunk -> chunk.length() <= 10_000);
        assertThat(chunks.get(chunks.size() - 1)).contains("TAIL_REQUIREMENT");
    }

    @Test
    void split_shouldPreferNaturalBoundaryWhenAvailable() {
        String text = "第一章\n".repeat(200) + "第二章关键要求";

        List<String> chunks = TenderDocumentTextChunker.split(text, 500, 50);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0)).endsWith("\n");
    }
}
