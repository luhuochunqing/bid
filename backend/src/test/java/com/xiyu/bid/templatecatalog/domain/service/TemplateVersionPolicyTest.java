package com.xiyu.bid.templatecatalog.domain.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateVersionPolicyTest {

    private final TemplateVersionPolicy policy = new TemplateVersionPolicy();

    @Test
    void initialVersion_ShouldAlwaysBeOnePointZero() {
        assertThat(policy.initialVersion()).isEqualTo("1.0");
    }

    @Test
    void nextVersion_ShouldIncrementMinorVersion() {
        assertThat(policy.nextVersion("1.0")).isEqualTo("1.1");
    }

    @Test
    void nextVersion_ShouldFallbackWhenCurrentVersionMissing() {
        assertThat(policy.nextVersion(null)).isEqualTo("1.0");
        assertThat(policy.nextVersion("")).isEqualTo("1.0");
    }

    @Test
    void nextVersion_ShouldAppendFallbackSuffixForUnexpectedFormats() {
        assertThat(policy.nextVersion("v1")).isEqualTo("v1.1");
    }
}
