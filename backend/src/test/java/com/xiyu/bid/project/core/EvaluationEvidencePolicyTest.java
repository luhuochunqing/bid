package com.xiyu.bid.project.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link EvaluationEvidencePolicy}.
 * No Spring context, no mocks — verifies evidence uploaded/not-uploaded decisions.
 */
class EvaluationEvidencePolicyTest {

    @Test
    void checkEvidenceUploaded_permits_whenHasEvidence() {
        var decision = EvaluationEvidencePolicy.checkEvidenceUploaded(true);
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isNull();
    }

    @Test
    void checkEvidenceUploaded_denies_whenNoEvidence() {
        var decision = EvaluationEvidencePolicy.checkEvidenceUploaded(false);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("请上传评标文件");
    }
}
