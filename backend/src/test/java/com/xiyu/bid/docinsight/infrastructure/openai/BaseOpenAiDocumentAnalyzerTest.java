package com.xiyu.bid.docinsight.infrastructure.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.docinsight.application.DocumentAnalysisInput;
import com.xiyu.bid.docinsight.application.DocumentAnalysisResult;
import com.xiyu.bid.docinsight.domain.DocumentChunk;
import com.xiyu.bid.docinsight.domain.StructuralDocumentChunker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BaseOpenAiDocumentAnalyzer – delegation contract")
class BaseOpenAiDocumentAnalyzerTest {

    /**
     * 捕获调用序列的具体子类（匿名测试桩）。
     */
    static class CaptureAnalyzer extends BaseOpenAiDocumentAnalyzer<String> {

        final List<String> builtPrompts = new ArrayList<>();
        final List<String> requestedPrompts = new ArrayList<>();
        List<String> mergedResults = null;
        DocumentAnalysisInput mergedInput = null;

        CaptureAnalyzer(StructuralDocumentChunker chunker) {
            super(chunker);
        }

        @Override
        public boolean supports(String profileCode) {
            return true;
        }

        @Override
        protected String buildPrompt(DocumentAnalysisInput input, DocumentChunk chunk, int index, int total) {
            String prompt = "prompt-" + index + "-of-" + total;
            builtPrompts.add(prompt);
            return prompt;
        }

        @Override
        protected String requestAi(String prompt) {
            requestedPrompts.add(prompt);
            return "result-for-" + prompt;
        }

        @Override
        protected DocumentAnalysisResult mergeAndMap(DocumentAnalysisInput input, List<String> results) {
            mergedResults = new ArrayList<>(results);
            mergedInput = input;
            return new DocumentAnalysisResult(input.documentId(), Map.of(), List.of(), null, List.of());
        }
    }

    private CaptureAnalyzer analyzer;
    private DocumentAnalysisInput threeChunkInput;

    @BeforeEach
    void setUp() {
        StructuralDocumentChunker chunker = new StructuralDocumentChunker(new ObjectMapper());
        analyzer = new CaptureAnalyzer(chunker);

        List<DocumentChunk> chunks = List.of(
                new DocumentChunk("chunk-text-1", List.of("S1")),
                new DocumentChunk("chunk-text-2", List.of("S2")),
                new DocumentChunk("chunk-text-3", List.of("S3"))
        );
        threeChunkInput = new DocumentAnalysisInput(
                "doc://test", "test.pdf", "full text", null, chunks, "TEST", Map.of()
        );
    }

    @Test
    @DisplayName("analyze() 应为每个 chunk 调用一次 buildPrompt")
    void analyze_shouldCallBuildPromptOncePerChunk() {
        analyzer.analyze(threeChunkInput);

        assertThat(analyzer.builtPrompts).hasSize(3);
        assertThat(analyzer.builtPrompts.get(0)).isEqualTo("prompt-1-of-3");
        assertThat(analyzer.builtPrompts.get(1)).isEqualTo("prompt-2-of-3");
        assertThat(analyzer.builtPrompts.get(2)).isEqualTo("prompt-3-of-3");
    }

    @Test
    @DisplayName("analyze() 应为每个 chunk 调用一次 requestAi")
    void analyze_shouldCallRequestAiOncePerChunk() {
        analyzer.analyze(threeChunkInput);

        assertThat(analyzer.requestedPrompts).hasSize(3);
        assertThat(analyzer.requestedPrompts.get(0)).isEqualTo("prompt-1-of-3");
        assertThat(analyzer.requestedPrompts.get(1)).isEqualTo("prompt-2-of-3");
        assertThat(analyzer.requestedPrompts.get(2)).isEqualTo("prompt-3-of-3");
    }

    @Test
    @DisplayName("analyze() 应最终调用一次 mergeAndMap，且按顺序传入所有 part results")
    void analyze_shouldCallMergeAndMapOnceWithAllResultsInOrder() {
        DocumentAnalysisResult result = analyzer.analyze(threeChunkInput);

        assertThat(analyzer.mergedResults).hasSize(3);
        assertThat(analyzer.mergedResults.get(0)).isEqualTo("result-for-prompt-1-of-3");
        assertThat(analyzer.mergedResults.get(1)).isEqualTo("result-for-prompt-2-of-3");
        assertThat(analyzer.mergedResults.get(2)).isEqualTo("result-for-prompt-3-of-3");

        // documentId passthrough
        assertThat(result.documentId()).isEqualTo("doc://test");
    }

    @Test
    @DisplayName("analyze() 在零 chunk 时应以空列表调用 mergeAndMap")
    void analyze_withNoChunks_shouldCallMergeWithEmptyList() {
        DocumentAnalysisInput emptyInput = new DocumentAnalysisInput(
                "doc://empty", "empty.pdf", "", null, List.of(), "TEST", Map.of()
        );

        analyzer.analyze(emptyInput);

        assertThat(analyzer.builtPrompts).isEmpty();
        assertThat(analyzer.requestedPrompts).isEmpty();
        assertThat(analyzer.mergedResults).isEmpty();
    }
}
