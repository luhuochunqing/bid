// Input: REVIEW_REQUIRED ScoreCandidate list
// Output: ScoreFact snapshot list for review UI
// Pos: scoreparse/application — legacy review candidate fact mapping
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreCodeSanitizer;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreItemOrderPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreItemTitleExtractor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class ScoreReviewCandidateFactMapper {

    private static final ScoreItemOrderPolicy ORDER_POLICY = new ScoreItemOrderPolicy();

    private ScoreReviewCandidateFactMapper() {
    }

    static List<ScoreFact> toFacts(Long parseTaskId, List<ScoreCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<ScoreCandidate> ordered = ORDER_POLICY.sort(candidates);
        List<ScoreFact> facts = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            facts.add(toFact(parseTaskId, i + 1, ordered.get(i)));
        }
        return List.copyOf(facts);
    }

    private static ScoreFact toFact(Long parseTaskId, int index, ScoreCandidate candidate) {
        String code = ScoreCodeSanitizer.clean(candidate.code());
        String dim = resolveDim(candidate);
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

    private static String resolveDim(ScoreCandidate candidate) {
        String trimmedDim = candidate.dim() == null ? "" : candidate.dim().trim();
        if (candidate.isParentOnlyDim() && candidate.detail() != null && !candidate.detail().isBlank()) {
            String title = ScoreItemTitleExtractor.extract(candidate.detail()).orElse("");
            if (!title.isBlank()) {
                return title.length() > 200 ? title.substring(0, 200) : title;
            }
        }
        if (!trimmedDim.isBlank()) {
            return trimmedDim.length() > 200 ? trimmedDim.substring(0, 200) : trimmedDim;
        }
        if (candidate.detail() != null && !candidate.detail().isBlank()) {
            String trimmed = candidate.detail().trim();
            return trimmed.length() > 50 ? trimmed.substring(0, 50) : trimmed;
        }
        return "评分项";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
