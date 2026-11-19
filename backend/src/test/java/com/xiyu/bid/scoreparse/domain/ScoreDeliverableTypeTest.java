package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreDeliverableTypeTest {

    @Test
    void normalize_validDeliverableTypes_returnsNormalizedName() {
        assertThat(ScoreDeliverableType.normalize("text_proposal")).isEqualTo("TEXT_PROPOSAL");
        assertThat(ScoreDeliverableType.normalize("SYSTEM_SCREENSHOT")).isEqualTo("SYSTEM_SCREENSHOT");
        assertThat(ScoreDeliverableType.normalize(" qualification_cert ")).isEqualTo("QUALIFICATION_CERT");
        assertThat(ScoreDeliverableType.normalize("CONTRACT_INVOICE")).isEqualTo("CONTRACT_INVOICE");
        assertThat(ScoreDeliverableType.normalize("other")).isEqualTo("OTHER");
    }

    @Test
    void normalize_nullOrBlank_returnsNull() {
        assertThat(ScoreDeliverableType.normalize(null)).isNull();
        assertThat(ScoreDeliverableType.normalize("")).isNull();
        assertThat(ScoreDeliverableType.normalize("   ")).isNull();
    }

    @Test
    void normalize_unknownValue_fallsBackToOther() {
        assertThat(ScoreDeliverableType.normalize("UNRECOGNIZED_TYPE")).isEqualTo("OTHER");
    }

    @Test
    void scoringMode_normalize_validScoringModes() {
        assertThat(ScoreScoringMode.normalize("subjective_review")).isEqualTo("SUBJECTIVE_REVIEW");
        assertThat(ScoreScoringMode.normalize("QUANTITATIVE_RANKING")).isEqualTo("QUANTITATIVE_RANKING");
        assertThat(ScoreScoringMode.normalize(" stepwise_scoring ")).isEqualTo("STEPWISE_SCORING");
        assertThat(ScoreScoringMode.normalize("deduction")).isEqualTo("DEDUCTION");
        assertThat(ScoreScoringMode.normalize("OTHER")).isEqualTo("OTHER");
    }

    @Test
    void scoringMode_normalize_nullOrBlank_returnsNull() {
        assertThat(ScoreScoringMode.normalize(null)).isNull();
        assertThat(ScoreScoringMode.normalize("")).isNull();
        assertThat(ScoreScoringMode.normalize("   ")).isNull();
    }

    @Test
    void scoringMode_normalize_unknownValue_fallsBackToOther() {
        assertThat(ScoreScoringMode.normalize("RANDOM_MODE")).isEqualTo("OTHER");
    }
}
