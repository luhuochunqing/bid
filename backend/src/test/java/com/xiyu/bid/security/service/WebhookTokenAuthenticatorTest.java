// Input: configured token + provided token candidates
// Output: contract checks for fail-closed validation behavior
// Pos: Test/Webhook 共享密钥校验单测
package com.xiyu.bid.security.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WebhookTokenAuthenticator")
class WebhookTokenAuthenticatorTest {

    @Test
    @DisplayName("rejects all tokens when configured secret is blank (fail-closed)")
    void rejects_WhenSecretBlank() {
        WebhookTokenAuthenticator auth = new WebhookTokenAuthenticator("");
        assertFalse(auth.isValidCrmToken("anything"));
        assertFalse(auth.isValidCrmToken(null));
    }

    @Test
    @DisplayName("rejects all tokens when configured secret is null (fail-closed)")
    void rejects_WhenSecretNull() {
        WebhookTokenAuthenticator auth = new WebhookTokenAuthenticator(null);
        assertFalse(auth.isValidCrmToken("anything"));
    }

    @Test
    @DisplayName("rejects null provided token even when secret is configured")
    void rejects_WhenProvidedNull() {
        WebhookTokenAuthenticator auth = new WebhookTokenAuthenticator("secret");
        assertFalse(auth.isValidCrmToken(null));
    }

    @Test
    @DisplayName("rejects mismatched provided token")
    void rejects_WhenMismatched() {
        WebhookTokenAuthenticator auth = new WebhookTokenAuthenticator("secret");
        assertFalse(auth.isValidCrmToken("wrong"));
        assertFalse(auth.isValidCrmToken("secre"));
        assertFalse(auth.isValidCrmToken("secret-extra"));
    }

    @Test
    @DisplayName("accepts exact match")
    void accepts_WhenExactMatch() {
        WebhookTokenAuthenticator auth = new WebhookTokenAuthenticator("secret");
        assertTrue(auth.isValidCrmToken("secret"));
    }

    @Test
    @DisplayName("trims whitespace from configured secret")
    void trims_ConfiguredSecret() {
        WebhookTokenAuthenticator auth = new WebhookTokenAuthenticator("  secret  ");
        assertTrue(auth.isValidCrmToken("secret"));
        assertFalse(auth.isValidCrmToken("  secret  "));
    }
}
