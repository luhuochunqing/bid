// Input: ScoringCriterionOutput (LLM POJO) → ScoringCriterion (domain record)
// Output: Domain record with sub-type classification
// Pos: biddraftagent/infrastructure/openai

package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.biddraftagent.domain.ScoringCriteriaClassificationPolicy;

final class ScoringCriterionMapper {

    private static final ScoringCriteriaClassificationPolicy POLICY = new ScoringCriteriaClassificationPolicy();

    private ScoringCriterionMapper() {}

    static ScoringCriterion toDomain(ScoringCriterionOutput output) {
        if (output == null) return null;
        String indicator = output.indicator != null ? output.indicator : "";
        return new ScoringCriterion(
                output.itemNumber,
                output.dimension,
                indicator,
                output.weight,
                POLICY.classify(indicator)
        );
    }
}
