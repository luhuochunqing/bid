package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.DualTrackScoreExtractor;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.PrimaryScoreFactPolicy;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/** One entry point: text extraction first; the old pipeline is a lazy fallback. */
@Slf4j
final class ScoreParseEntryRouter {
    private ScoreParseEntryRouter() {}

    record Route(DualTrackScoreExtractor.Result primary, StructuredTableResult fallback,
                 OriginalScoreEvidencePolicy.Total sourceTotal, String model) {
        boolean primaryAccepted() { return primary.track() == DualTrackScoreExtractor.Track.PRIMARY; }
    }

    static Route extract(TenderTextSource source, MultimodalScoreFactExtractor model, Runnable heartbeat,
                         Supplier<StructuredTableResult> fallback) {
        var route = attempt(source, model, heartbeat);
        return route.primaryAccepted() ? route : new Route(route.primary(), fallback.get(), route.sourceTotal(), route.model());
    }

    private static Route attempt(TenderTextSource source,
            MultimodalScoreFactExtractor model, Runnable heartbeat) {
        if (model != null && model.supportsTextOnly()) {
            try {
                var extraction = model.extractPrimaryTextAndProgress(source.text(), heartbeat);
                var textArtifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta(
                        source.fileName(), "primary-text", "text", "2.0"), List.of(), List.of());
                var context = OriginalScoringContext.resolve(textArtifact, source.text());
                var total = context.total().value();
                boolean conflict = "CONFLICT".equals(context.total().source())
                        || context.total().problems().stream().anyMatch(p -> "ORIGINAL_SCHEME_AMBIGUOUS".equals(p.code()))
                        || (extraction.declaredTotal() != null && (total == null ? java.math.BigDecimal.valueOf(100) : total)
                        .compareTo(extraction.declaredTotal()) != 0);
                if (!conflict) return new Route(DualTrackScoreExtractor.dispatch(
                        PrimaryScoreFactPolicy.candidates(extraction.facts(), source.text()), source.text(), total, List::of),
                        null, context.total(), extraction.model());
            } catch (RuntimeException exception) {
                log.warn("Primary score extraction failed; continuing with existing pipeline: {}", exception.getClass().getSimpleName());
            }
        }
        return new Route(DualTrackScoreExtractor.dispatch(List.of(), source.text(), null, List::of), null,
                new OriginalScoreEvidencePolicy.Total(null, "UNKNOWN", "", "", List.of()), "");
    }
}
