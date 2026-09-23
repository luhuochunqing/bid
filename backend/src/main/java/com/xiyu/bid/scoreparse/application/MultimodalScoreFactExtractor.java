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

    default ExtractionResult recheck(DocumentArtifact artifact) {
        return extract(artifact, List.of());
    }

    default ExtractionResult recheck(DocumentArtifact artifact, List<DocumentImage> pageImages) {
        return recheck(artifact);
    }

    record ExtractionResult(BigDecimal declaredTotal, List<MultimodalScoreFact> facts,
                            String model, int imageCount) {
        public ExtractionResult {
            facts = facts == null ? List.of() : List.copyOf(facts);
            model = model == null ? "" : model;
        }
    }
}
