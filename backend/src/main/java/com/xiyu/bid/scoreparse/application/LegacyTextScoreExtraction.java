// Input: complete tender text, real model port and optional source artifacts
// Output: validated candidates; model request failures escape to the task FAILED boundary
// Pos: scoreparse application default-off compatibility route
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LegacyTextScoreExtraction {
    private static final Logger log = LoggerFactory.getLogger(LegacyTextScoreExtraction.class);
    private LegacyTextScoreExtraction() {}
    static StructuredTableResult extract(TenderTextSource source, List<LoadedBidFile> files,
            List<TenderTextSource> sources, SidecarDocumentArtifactExtractor artifacts,
            MultimodalScoreFactExtractor model, Runnable heartbeat) {
        if (!model.supportsTextOnly()) return MultimodalStructuredExtractionHandler.legacyModelPath(source,
                files.isEmpty() ? null : files.get(0), artifacts, model, heartbeat);
        MultimodalScoreFactExtractor.ExtractionResult extracted;
        boolean rechecked = false;
        try { extracted = model.extractTextAndProgress(source.text(), heartbeat); }
        catch (ScoreModelRequestException failure) { throw failure; }
        catch (RuntimeException firstFailure) {
            log.warn("Legacy text first extraction failed: file={}", source.fileName(), firstFailure);
            extracted = model.recheckTextAndProgress(source.text(), heartbeat);
            rechecked = true;
        }
        DocumentArtifact artifact = files.isEmpty() || artifacts == null ? null
                : SourceArtifactAcquisition.acquire(files, sources, artifacts).artifact();
        return MultimodalStructuredExtractionHandler.run(source.fileName(), source.text(), artifact,
                OriginalScoringContext.resolve(artifact, source.text()), model, heartbeat, extracted,
                true, rechecked, false);
    }
}
