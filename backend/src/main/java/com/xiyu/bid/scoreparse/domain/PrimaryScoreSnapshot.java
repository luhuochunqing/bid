package com.xiyu.bid.scoreparse.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Versioned projection of already verified primary candidates, with real text ranges and one explicit group. */
public final class PrimaryScoreSnapshot {
    private PrimaryScoreSnapshot() {}

    public static ScoreExtractionSnapshot from(String taskId, String fileName, String text,
            List<ScoreCandidate> candidates, OriginalScoreEvidencePolicy.Total total, String model) {
        String document = "text:" + ScoreVersionFingerprint.hash(text);
        String scheme = "primary:" + ScoreVersionFingerprint.hash(document);
        var source = EvidenceText.normalize(text);
        List<ScoreFact> facts = new ArrayList<>();
        List<OriginalScoreEvidencePolicy.Qualification> qualifications = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            var candidate = candidates.get(i);
            var range = source.uniqueSpan(candidate.detail()).orElseThrow();
            String id = "primary-" + i;
            String locator = "text:" + range.start() + "-" + range.end();
            String raw = text.substring(range.start(), range.end());
            var span = new SourceSpan(document, "", "text", scheme, null, "", "text:document",
                    range.start(), range.end(), raw, locator);
            var metadata = new ScoreSourceMetadata(raw, raw, List.of(span), List.of(), "", candidate.scoreTypeGuess());
            facts.add(new ScoreFact(id, "", List.of(locator), raw, candidate.code(), candidate.dim(), candidate.weight(),
                    new ScoreEvidence(ScoreEvidence.Kind.AWARD_RULE, raw, locator, range.start(), range.end(), "Verified text maximum"),
                    ScoreRule.MAX_SCORE, "", "DUAL_TRACK_PRIMARY", 1, ScoreFact.ValidationStatus.AUTO_APPROVED,
                    ChildAggregation.SUM, metadata));
            qualifications.add(new OriginalScoreEvidencePolicy.Qualification(id, scheme, "IN_SCHEME", true, "", locator,
                    fileName, total.value(), total.value() == null ? "UNKNOWN" : "VERIFIED", total.quote(),
                    total.locator(), "TEXT_VERIFIED", List.of(), ChildAggregation.SUM));
        }
        var snapshot = ScoreExtractionSnapshot.from(taskId, facts, qualifications, List.of());
        return new ScoreExtractionSnapshot(snapshot.payloadVersion(), snapshot.items(), snapshot.schemes(),
                snapshot.sourceDiagnostics(), Map.of("pipelineVersion", "DUAL_TRACK_PRIMARY", "llmModel", model,
                "totalSource", total.value() == null ? "DEFAULT_100_CONTRACT" : total.source()));
    }
}
