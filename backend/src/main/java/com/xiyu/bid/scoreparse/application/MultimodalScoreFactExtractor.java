package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.DocumentImage;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.List;

/** Port for one-shot multimodal score fact extraction. */
public interface MultimodalScoreFactExtractor {
    int MAX_SCORE_PAGE_IMAGES = 24;

    ExtractionResult extract(DocumentArtifact artifact, List<DocumentImage> pageImages);

    /** Extract score facts from source text plus the canonical table grid. */
    default ExtractionResult extractWithText(DocumentArtifact artifact, String sourceText) {
        throw new UnsupportedOperationException("Text-backed score extraction is not implemented");
    }

    default ExtractionResult extractWithTextAndProgress(DocumentArtifact artifact, String sourceText,
                                                        Runnable chunkCompleted) {
        return extractWithText(artifact, sourceText);
    }

    default ExtractionResult recheck(DocumentArtifact artifact) {
        return extract(artifact, List.of());
    }

    default ExtractionResult recheck(DocumentArtifact artifact, List<DocumentImage> pageImages) {
        return recheck(artifact);
    }

    /** Recheck using the same text evidence supplied to the initial extraction. */
    default ExtractionResult recheckWithText(DocumentArtifact artifact, String sourceText) {
        return extractWithText(artifact, sourceText);
    }

    default ExtractionResult recheckWithTextAndProgress(DocumentArtifact artifact, String sourceText,
                                                        Runnable chunkCompleted) {
        return recheckWithText(artifact, sourceText);
    }

    record ExtractionResult(BigDecimal declaredTotal, List<MultimodalScoreFact> facts,
                            String model, int imageCount) {
        public ExtractionResult {
            facts = facts == null ? List.of() : List.copyOf(facts);
            model = model == null ? "" : model;
        }
    }
}
