package com.xiyu.bid.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SecurityConfig#shouldAllowDevTooling(String[])}.
 *
 * <p>The Java profile gate must mirror the shell guard taxonomy enforced by
 * the dev-tooling guards (e.g. {@code backend/start.sh},
 * {@code scripts/dev-services.sh}): any prod-like signal (prod,
 * production, staging, stg, release, live, uat, canary) must disable the
 * dev allowlist even if the dev/e2e profile is also present. Empty profile
 * lists must default to closed (no dev tooling).
 */
class SecurityConfigProfileGateTest {

    @Test
    void uatPlusE2eMustNotEnableDevTooling() {
        // Regression guard: SPRING_PROFILES_ACTIVE=uat,e2e on a UAT/canary host
        // must NOT expose swagger/h2/api-docs anonymously.
        assertThat(SecurityConfig.shouldAllowDevTooling(new String[]{"uat", "e2e"})).isFalse();
    }

    @Test
    void releaseProfileMustNotEnableDevTooling() {
        assertThat(SecurityConfig.shouldAllowDevTooling(new String[]{"release"})).isFalse();
    }

    @Test
    void canaryProfileMustNotEnableDevTooling() {
        assertThat(SecurityConfig.shouldAllowDevTooling(new String[]{"canary"})).isFalse();
    }

    @Test
    void liveProfileMustNotEnableDevTooling() {
        assertThat(SecurityConfig.shouldAllowDevTooling(new String[]{"live"})).isFalse();
    }

    @Test
    void stagingProfileMustNotEnableDevTooling() {
        assertThat(SecurityConfig.shouldAllowDevTooling(new String[]{"staging"})).isFalse();
    }

    @Test
    void stgProfileMustNotEnableDevTooling() {
        assertThat(SecurityConfig.shouldAllowDevTooling(new String[]{"stg"})).isFalse();
    }

    @Test
    void prodProfileMustNotEnableDevTooling() {
        assertThat(SecurityConfig.shouldAllowDevTooling(new String[]{"prod"})).isFalse();
    }

    @Test
    void productionProfileMustNotEnableDevTooling() {
        assertThat(SecurityConfig.shouldAllowDevTooling(new String[]{"production"})).isFalse();
    }

    @Test
    void devProfileEnablesDevTooling() {
        assertThat(SecurityConfig.shouldAllowDevTooling(new String[]{"dev"})).isTrue();
    }

    @Test
    void e2eProfileEnablesDevTooling() {
        assertThat(SecurityConfig.shouldAllowDevTooling(new String[]{"e2e"})).isTrue();
    }

    @Test
    void devPlusMysqlEnablesDevTooling() {
        assertThat(SecurityConfig.shouldAllowDevTooling(new String[]{"dev", "mysql"})).isTrue();
    }

    @Test
    void emptyProfileListFailsClosed() {
        // Defense in depth: when SPRING_PROFILES_ACTIVE is unset (no profiles),
        // dev tooling must NOT be exposed.
        assertThat(SecurityConfig.shouldAllowDevTooling(new String[]{})).isFalse();
    }

    @Test
    void nullProfileArrayFailsClosed() {
        assertThat(SecurityConfig.shouldAllowDevTooling(null)).isFalse();
    }

    @Test
    void prodMixedWithDevStillBlocksDevTooling() {
        // Even if dev is also active, any prod-like profile must close the gate.
        assertThat(SecurityConfig.shouldAllowDevTooling(new String[]{"dev", "prod"})).isFalse();
    }

    @Test
    void caseInsensitiveProdLikeProfileMatching() {
        assertThat(SecurityConfig.shouldAllowDevTooling(new String[]{"PROD"})).isFalse();
        assertThat(SecurityConfig.shouldAllowDevTooling(new String[]{"UAT"})).isFalse();
        assertThat(SecurityConfig.shouldAllowDevTooling(new String[]{"Production"})).isFalse();
    }
}
