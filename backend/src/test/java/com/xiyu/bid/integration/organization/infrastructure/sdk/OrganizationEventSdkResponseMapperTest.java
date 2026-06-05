package com.xiyu.bid.integration.organization.infrastructure.sdk;

import com.xiyu.bid.integration.organization.dto.OrganizationEventWebhookResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrganizationEventSdkResponseMapper")
class OrganizationEventSdkResponseMapperTest {

    private OrganizationEventSdkResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OrganizationEventSdkResponseMapper();
    }

    @Test
    @DisplayName("code=200 maps to PROCESSED status with accepted=true")
    void mapsSuccessCode() {
        OrganizationEventWebhookResponse resp = mapper.toResponse("evt-001", 200, "ok");

        assertThat(resp.code()).isEqualTo("200");
        assertThat(resp.data().accepted()).isTrue();
        assertThat(resp.data().duplicate()).isFalse();
        assertThat(resp.data().status()).isEqualTo("PROCESSED");
    }

    @Test
    @DisplayName("code=4xx maps to REJECTED status with accepted=false")
    void mapsClientError() {
        OrganizationEventWebhookResponse resp = mapper.toResponse("evt-002", 400, "bad request");

        assertThat(resp.code()).isEqualTo("400");
        assertThat(resp.data().accepted()).isFalse();
        assertThat(resp.data().status()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("code=5xx maps to PENDING_RETRY status with accepted=false")
    void mapsServerError() {
        OrganizationEventWebhookResponse resp = mapper.toResponse("evt-003", 500, "internal error");

        assertThat(resp.code()).isEqualTo("500");
        assertThat(resp.data().accepted()).isFalse();
        assertThat(resp.data().status()).isEqualTo("PENDING_RETRY");
    }

    @Test
    @DisplayName("SDK exception maps to PENDING_RETRY")
    void mapsException() {
        OrganizationEventWebhookResponse resp = mapper.fromException("evt-004", new RuntimeException("connect timeout"));

        assertThat(resp.code()).isEqualTo("500");
        assertThat(resp.data().accepted()).isFalse();
        assertThat(resp.data().status()).isEqualTo("PENDING_RETRY");
        assertThat(resp.msg()).contains("sdk exception");
    }

    @Test
    @DisplayName("null message defaults to ok for success path")
    void nullMessageDefaults() {
        OrganizationEventWebhookResponse resp = mapper.toResponse("evt-005", 200, null);

        assertThat(resp.code()).isEqualTo("200");
        assertThat(resp.msg()).isEqualTo("ok");
    }

    @Test
    @DisplayName("null exception message handled gracefully")
    void nullExceptionMessage() {
        OrganizationEventWebhookResponse resp = mapper.fromException("evt-006", null);

        assertThat(resp.code()).isEqualTo("500");
        assertThat(resp.msg()).contains("unknown");
    }
}
