package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

/** Default-off compatibility: old text-first model call, then optional evidence binding. */
final class LegacyTextScoreExtraction {
    private static final Logger log = LoggerFactory.getLogger(LegacyTextScoreExtraction.class);

    private LegacyTextScoreExtraction() {}

    static StructuredTableResult extract(TenderTextSource source, List<LoadedBidFile> files, List<TenderTextSource> sources,
            SidecarDocumentArtifactExtractor artifacts, MultimodalScoreFactExtractor model, Runnable heartbeat) {
        if (!model.supportsTextOnly()) return MultimodalStructuredExtractionHandler.legacyModelPath(source,
                files.isEmpty() ? null : files.get(0), artifacts, model, heartbeat);
        try {
            MultimodalScoreFactExtractor.ExtractionResult extracted;
            boolean rechecked = false;
            try {
                extracted = model.extractTextAndProgress(source.text(), heartbeat);
            } catch (RuntimeException firstFailure) {
                log.warn("LegacyTextScoreExtraction 首次文字抽取失败，尝试模型复核: file={}, type={}, msg={}",
                        source.fileName(), firstFailure.getClass().getName(), firstFailure.getMessage(), firstFailure);
                extracted = model.recheckTextAndProgress(source.text(), heartbeat);
                rechecked = true;
            }
            DocumentArtifact artifact = files.isEmpty() || artifacts == null ? null : SourceArtifactAcquisition.acquire(files, sources, artifacts).artifact();
            return MultimodalStructuredExtractionHandler.run(source.fileName(), source.text(), artifact,
                    OriginalScoringContext.resolve(artifact, source.text()), model, heartbeat, extracted, true, rechecked, false);
        } catch (RuntimeException failure) {
            log.error("LegacyTextScoreExtraction 文字评分事实提取失败: file={}, type={}, msg={}",
                    source.fileName(), failure.getClass().getName(), failure.getMessage(), failure);
            return StructuredTableResult.structureIncomplete("MULTIMODAL_EXTRACTION_FAILED");
        }
    }
}
