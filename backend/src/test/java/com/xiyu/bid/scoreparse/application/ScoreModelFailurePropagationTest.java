package com.xiyu.bid.scoreparse.application;

import java.util.List;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScoreModelFailurePropagationTest {
    @Test void legacyTransportFailureEscapesWithoutRepeatingAllModelChunks() {
        var model = mock(MultimodalScoreFactExtractor.class);
        when(model.supportsTextOnly()).thenReturn(true);
        var cause = new IllegalStateException("upstream-private-key-and-prompt", new SocketTimeoutException());
        var failure = new ScoreModelRequestException(cause);
        when(model.extractTextAndProgress(anyString(), any())).thenThrow(failure);
        var source = new TenderTextSource("synthetic.pdf", null, "采购要求");
        assertThatThrownBy(() -> LegacyTextScoreExtraction.extract(source, List.of(), List.of(source),
                null, model, () -> {})).isSameAs(failure);
        verify(model, never()).recheckTextAndProgress(anyString(), any());
        assertThat(failure.getMessage()).contains("超时").doesNotContain("private", "prompt");
        assertThat(failure.getCause()).isSameAs(cause);
    }
    @Test void versionedTransportFailureEscapesWithoutStructuralFallbackOrModelRetry() {
        var model = mock(MultimodalScoreFactExtractor.class);
        when(model.supportsTextOnly()).thenReturn(true);
        var failure = new ScoreModelRequestException(new IllegalStateException("upstream-secret"));
        when(model.extractTextAndProgress(anyString(), any())).thenThrow(failure);
        var source = new TenderTextSource("synthetic.pdf", null, "采购要求");
        assertThatThrownBy(() -> MultimodalStructuredExtractionHandler.extractWithTextModelHeartbeat(source,
                List.of(), List.of(source), null, model, () -> {})).isSameAs(failure);
        verify(model, never()).recheckTextAndProgress(anyString(), any());
    }
    @Test void semanticRecheckTransportFailureIsNotSwallowedAsReviewResult() {
        var model = mock(MultimodalScoreFactExtractor.class);
        when(model.supportsTextOnly()).thenReturn(true);
        when(model.extractTextAndProgress(anyString(), any())).thenReturn(
                new MultimodalScoreFactExtractor.ExtractionResult(null, List.of(), "synthetic", 0));
        var failure = new ScoreModelRequestException(new IllegalStateException("private", new SocketTimeoutException()));
        when(model.recheckTextAndProgress(anyString(), any())).thenThrow(failure);
        var source = new TenderTextSource("synthetic.pdf", null, "采购要求");
        assertThatThrownBy(() -> LegacyTextScoreExtraction.extract(source, List.of(), List.of(source),
                null, model, () -> {})).isSameAs(failure);
        verify(model, times(1)).extractTextAndProgress(anyString(), any());
        verify(model, times(1)).recheckTextAndProgress(anyString(), any());
    }

}
