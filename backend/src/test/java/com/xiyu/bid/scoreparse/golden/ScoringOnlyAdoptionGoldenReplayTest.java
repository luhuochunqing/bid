package com.xiyu.bid.scoreparse.golden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.application.ScoreFactShadowPipeline;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic mixed-table shape; no private customer input is stored in Git. */
class ScoringOnlyAdoptionGoldenReplayTest {
    @Test void mixedReviewTableRetainsEvidenceButOnlyPublishesScoredItems() throws Exception {
        var json = new ObjectMapper();
        var source = json.readValue(GoldenCasesReplaySupport.readFixture(
                "case-scoring-only-adoption.artifact.json"), DocumentArtifact.class);
        var expected = json.readTree(GoldenCasesReplaySupport.readFixture(
                "case-scoring-only-adoption.expected.json"));
        var facts = ScoreFactShadowPipeline.run(source, "本项目评分总分100分").tables().stream()
                .flatMap(t -> t.facts().stream()).toList();
        assertThat(facts).hasSize(expected.path("factCount").asInt());
        var qualifications = OriginalScoreEvidencePolicy.verifyFacts(source, "本项目评分总分100分", facts).qualifications();
        assertThat(qualifications.stream().filter(OriginalScoreEvidencePolicy.Qualification::candidateEligible)).hasSize(expected.path("factCount").asInt());
        var inputs = facts.stream().map(f -> new ScoreReviewAdoptionPolicy.CandidateInput(f.factId(), f.code(),
                f.dimension(), f.weight(), f.sourceText(), f.parentFactId())).toList();
        var selected = facts.stream().map(f -> f.factId()).collect(Collectors.toSet());
        var result = new ScoreReviewAdoptionPolicy().adopt(inputs, selected, Map.of());
        assertThat(result.success()).isTrue();
        assertThat(result.totalWeight()).isEqualByComparingTo(expected.path("total").decimalValue());
        assertThat(result.candidates()).hasSize(expected.path("items").size());
        for (int i = 0; i < result.candidates().size(); i++) {
            assertThat(result.candidates().get(i).dim()).isEqualTo(expected.path("items").get(i).path("dim").asText());
            assertThat(result.candidates().get(i).weight()).isEqualByComparingTo(expected.path("items").get(i).path("weight").decimalValue());
        }
    }
}
