package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.DocumentImage;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.List;

/** Bridges existing image-shaped test fakes to the text-backed extraction contract. */
abstract class TextAwareScoreFactExtractorTestAdapter implements MultimodalScoreFactExtractor {
    @Override
    public ExtractionResult extractWithText(DocumentArtifact artifact, String sourceText) {
        return extract(artifact, List.of());
    }

    @Override
    public ExtractionResult recheckWithText(DocumentArtifact artifact, String sourceText) {
        return recheck(artifact, List.of());
    }

    @Override
    public ExtractionResult extract(DocumentArtifact artifact, List<DocumentImage> images) {
        throw new UnsupportedOperationException("Test extractor must define extraction behavior");
    }
}
