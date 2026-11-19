// Input: REVIEW_REQUIRED ScoreCandidate list
// Output: ScoreFact snapshots and candidate type evidence for review UI
// Pos: scoreparse/application — legacy review candidate fact mapping
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreCodeSanitizer;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreItemOrderPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreItemTitleExtractor;
import com.xiyu.bid.scoreparse.entity.ScoreParseEvidence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class ScoreReviewCandidateFactMapper {

    private static final ScoreItemOrderPolicy ORDER_POLICY = new ScoreItemOrderPolicy();

    private ScoreReviewCandidateFactMapper() {
    }

    record ReviewSnapshot(List<ScoreFact> facts, List<ScoreParseEvidence> evidence) {}

    static ReviewSnapshot toSnapshot(Long parseTaskId, List<ScoreCandidate> candidates) {
        List<ScoreCandidate> ordered = ORDER_POLICY.sort(candidates);
        List<ScoreFact> facts = new ArrayList<>();
        List<ScoreParseEvidence> evidence = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            ScoreCandidate candidate = ordered.get(i);
            ScoreFact fact = toFact(parseTaskId, i + 1, candidate);
            facts.add(fact);
            String guess = candidate.scoreTypeGuess();
            if ("OBJECTIVE".equals(guess) || "SUBJECTIVE".equals(guess)) {
                evidence.add(ScoreParseEvidence.builder().parseTaskId(parseTaskId).factId(fact.factId())
                        .kind("CANDIDATE_SCORE_TYPE").explanation("scoreType=" + guess).build());
            }
        }
        return new ReviewSnapshot(List.copyOf(facts), List.copyOf(evidence));
    }

    private static ScoreFact toFact(Long parseTaskId, int index, ScoreCandidate candidate) {
        String code = ScoreCodeSanitizer.clean(candidate.code());
        String dim = ScoreItemTitleExtractor.resolveCandidateDimension(candidate, false);
        return new ScoreFact(
                "review-candidate-" + parseTaskId + "-" + index + "-" + code,
                "legacy-review-candidates",
                List.of(),
                firstNonBlank(candidate.sourceText(), candidate.detail(), dim),
                code,
                dim,
                candidate.weight() == null ? BigDecimal.ZERO : candidate.weight(),
                null,
                null,
                "",
                firstNonBlank(candidate.semanticPattern(), "LEGACY_REVIEW_CANDIDATE"),
                0.5,
                ScoreFact.ValidationStatus.REVIEW_REQUIRED
        );
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
