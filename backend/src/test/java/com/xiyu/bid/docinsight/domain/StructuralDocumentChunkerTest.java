package com.xiyu.bid.docinsight.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructuralDocumentChunkerTest {

    private static final int MAX_CHARS = 4000;
    private static final int OVERLAP_CHARS = 200;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StructuralDocumentChunker chunker = new StructuralDocumentChunker(objectMapper);

    // ── Case 1: well-formed sections split ────────────────────────────────────
    @Test
    void chunk_withMetadata_shouldSplitBySectionsAndPreservePath() {
        String text = "Title\nSection 1 Content\nSection 2 Content";
        String metadata = """
                {
                  "sections": [
                    {"heading": "Section 1", "charStart": 6, "charEnd": 23, "path": ["Chapter 1", "Section 1"]},
                    {"heading": "Section 2", "charStart": 24, "charEnd": 41, "path": ["Chapter 1", "Section 2"]}
                  ]
                }
                """;

        List<DocumentChunk> chunks = chunker.chunk(text, metadata);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).text()).contains("Section 1 Content");
        assertThat(chunks.get(0).sectionPath()).containsExactly("Chapter 1", "Section 1");
        assertThat(chunks.get(1).text()).contains("Section 2 Content");
        assertThat(chunks.get(1).sectionPath()).containsExactly("Chapter 1", "Section 2");
    }

    // ── Case 2: null metadata falls back to legacy (no section path) ──────────
    @Test
    void chunk_noMetadata_shouldFallbackToLegacy() {
        String text = "A".repeat(5000);
        List<DocumentChunk> chunks = chunker.chunk(text, null);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).sectionPath()).isEmpty();
    }

    // ── Case 3: malformed JSON falls back to legacy ───────────────────────────
    @Test
    void chunk_malformedJson_shouldFallbackToLegacy() {
        String text = "B".repeat(5000);
        String malformedMetadata = "{ this is not valid json }";

        List<DocumentChunk> chunks = chunker.chunk(text, malformedMetadata);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).sectionPath()).isEmpty();
    }

    // ── Case 4: oversized section is sub-chunked ──────────────────────────────
    @Test
    void chunk_oversizedSection_shouldBeSubChunked() {
        // Section text is 6000 chars > MAX_CHARS(4000); expect >1 chunk from it
        String sectionText = "X".repeat(6000);
        String text = "prefix" + sectionText;
        int sectionStart = 6;
        int sectionEnd = 6 + 6000;
        String metadata = """
                {
                  "sections": [
                    {"heading": "Big Section", "charStart": %d, "charEnd": %d, "path": ["Big"]}
                  ]
                }
                """.formatted(sectionStart, sectionEnd);

        List<DocumentChunk> chunks = chunker.chunk(text, metadata);

        assertThat(chunks).hasSizeGreaterThan(1);
        chunks.forEach(c -> assertThat(c.sectionPath()).containsExactly("Big"));
    }

    // ── Case 5: well-formed sections produce chunks in correct order ──────────
    @Test
    void chunk_wellFormedSections_shouldProduceChunksInSectionOrder() {
        String text = "AAABBBCCC";
        String metadata = """
                {
                  "sections": [
                    {"heading": "S1", "charStart": 0, "charEnd": 3, "path": ["S1"]},
                    {"heading": "S2", "charStart": 3, "charEnd": 6, "path": ["S2"]},
                    {"heading": "S3", "charStart": 6, "charEnd": 9, "path": ["S3"]}
                  ]
                }
                """;

        List<DocumentChunk> chunks = chunker.chunk(text, metadata);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).text()).isEqualTo("AAA");
        assertThat(chunks.get(1).text()).isEqualTo("BBB");
        assertThat(chunks.get(2).text()).isEqualTo("CCC");
        assertThat(chunks.get(0).sectionPath()).containsExactly("S1");
        assertThat(chunks.get(1).sectionPath()).containsExactly("S2");
        assertThat(chunks.get(2).sectionPath()).containsExactly("S3");
    }

    // ── Case 6: empty sections array falls back to legacy ─────────────────────
    @Test
    void chunk_emptySectionsArray_shouldFallbackToLegacy() {
        String text = "C".repeat(5000);
        String metadata = """
                {
                  "sections": []
                }
                """;

        List<DocumentChunk> chunks = chunker.chunk(text, metadata);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).sectionPath()).isEmpty();
    }

    // ── Case 7: missing "sections" key in JSON falls back to legacy ───────────
    @Test
    void chunk_jsonWithoutSectionsKey_shouldFallbackToLegacy() {
        String text = "D".repeat(5000);
        String metadata = """
                {
                  "otherKey": "value"
                }
                """;

        List<DocumentChunk> chunks = chunker.chunk(text, metadata);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).sectionPath()).isEmpty();
    }

    // ── Case 8: section-boundary overlap regression ───────────────────────────
    // The trailing ~OVERLAP_CHARS (200) chars of chunk[i] MUST appear at the
    // start of chunk[i+1] when a large section is sub-chunked.
    @Test
    void chunk_oversizedSection_secondChunkShouldStartWithOverlapFromFirst() {
        // 5000 'E' chars followed by 1000 'F' chars – no newlines so TextChunker
        // does not shift the boundary, giving deterministic split points.
        String sectionBody = "E".repeat(MAX_CHARS) + "F".repeat(1000);
        String text = sectionBody;
        String metadata = """
                {
                  "sections": [
                    {"heading": "Overlap", "charStart": 0, "charEnd": %d, "path": ["Overlap"]}
                  ]
                }
                """.formatted(sectionBody.length());

        List<DocumentChunk> chunks = chunker.chunk(text, metadata);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);

        String firstChunk = chunks.get(0).text();
        String secondChunk = chunks.get(1).text();

        // The last OVERLAP_CHARS of the first chunk must be the prefix of the second chunk
        String expectedOverlap = firstChunk.substring(firstChunk.length() - OVERLAP_CHARS);
        assertThat(secondChunk).startsWith(expectedOverlap);
    }
}
