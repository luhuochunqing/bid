package com.xiyu.bid.workflowform.infrastructure.oa;

import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.service.SettingsService;
import com.xiyu.bid.workflowform.domain.OaApprovalStatus;
import com.xiyu.bid.workflowform.dto.OaCallbackRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OaCallbackVerifierTest {

    private static final String SECRET = "oa-callback-secret";

    @Test
    void accepts_valid_signature_inside_time_window() {
        OaCallbackVerifier verifier = newVerifierWithSettingsSecret(SECRET);
        OaCallbackRequest request = request(String.valueOf(Instant.now().getEpochSecond()), "evt-1", null);

        assertThatCode(() -> verifier.verify(request)).doesNotThrowAnyException();
    }

    @Test
    void accepts_when_settings_secret_missing_but_property_secret_present() {
        SettingsService settingsService = settingsWithIntegrationApiKey("");
        OaCallbackVerifier verifier = new OaCallbackVerifier(settingsService, SECRET);
        OaCallbackRequest request = request(String.valueOf(Instant.now().getEpochSecond()), "evt-2", null);

        assertThatCode(() -> verifier.verify(request)).doesNotThrowAnyException();
    }

    @Test
    void uses_default_settings_key_if_not_overridden_is_not_treated_as_secret() {
        OaCallbackVerifier verifier = newVerifierWithSettingsSecret("sk_xiyu_bid_server_default");
        OaCallbackRequest request = request(String.valueOf(Instant.now().getEpochSecond()), "evt-3", null);

        assertThatThrownBy(() -> verifier.verify(request)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejects_bad_signature() {
        OaCallbackVerifier verifier = newVerifierWithSettingsSecret(SECRET);
        OaCallbackRequest request = request(String.valueOf(Instant.now().getEpochSecond()), "evt-4", "bad-signature");

        assertThatThrownBy(() -> verifier.verify(request)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejects_expired_timestamp() {
        OaCallbackVerifier verifier = newVerifierWithSettingsSecret(SECRET);
        OaCallbackRequest request = request(String.valueOf(Instant.now().minusSeconds(301).getEpochSecond()), "evt-5", null);

        assertThatThrownBy(() -> verifier.verify(request)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejects_non_numeric_timestamp() {
        OaCallbackVerifier verifier = newVerifierWithSettingsSecret(SECRET);
        OaCallbackRequest request = request("not-a-timestamp", "evt-6", "bad-signature");

        assertThatThrownBy(() -> verifier.verify(request)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejects_blank_secret_configuration() {
        OaCallbackVerifier verifier = new OaCallbackVerifier(mock(SettingsService.class), "");
        OaCallbackRequest request = request(String.valueOf(Instant.now().getEpochSecond()), "evt-7", null);

        assertThatThrownBy(() -> verifier.verify(request)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejects_missing_request_fields() {
        OaCallbackVerifier verifier = newVerifierWithSettingsSecret(SECRET);
        OaCallbackRequest request = new OaCallbackRequest(
                null,
                OaApprovalStatus.APPROVED,
                "经理",
                "同意",
                null,
                "",
                null,
                "evt-8"
        );

        assertThatThrownBy(() -> verifier.verify(request)).isInstanceOf(ResponseStatusException.class);
    }

    private static OaCallbackVerifier newVerifierWithSettingsSecret(String settingsSecret) {
        return new OaCallbackVerifier(settingsWithIntegrationApiKey(settingsSecret), "");
    }

    private static SettingsService settingsWithIntegrationApiKey(String apiKey) {
        SettingsResponse.IntegrationConfig integrationConfig = SettingsResponse.IntegrationConfig.builder()
                .apiKey(apiKey)
                .build();
        SettingsResponse settings = SettingsResponse.builder()
                .integrationConfig(integrationConfig)
                .build();
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);
        return settingsService;
    }

    private static OaCallbackRequest request(String timestamp, String eventId, String signatureOverride) {
        String signature = signatureOverride == null
                ? sign(String.join("|", "OA-1", OaApprovalStatus.APPROVED.name(), timestamp, "nonce-1", eventId))
                : signatureOverride;
        return new OaCallbackRequest("OA-1", OaApprovalStatus.APPROVED, "经理", "同意", timestamp, "nonce-1", signature, eventId);
    }

    private static String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
