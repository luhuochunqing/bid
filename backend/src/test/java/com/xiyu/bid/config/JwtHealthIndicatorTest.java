package com.xiyu.bid.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtHealthIndicator
 *
 * Tests JWT health check behavior with different secret configurations:
 * - Null secret (down)
 * - Short secret (down)
 * - Acceptable length (up)
 * - Strong length (up with strength indicator)
 */
@DisplayName("JWT Health Indicator Tests")
class JwtHealthIndicatorTest {

    /**
     * Test that health check returns DOWN when JWT secret is null.
     */
    @Test
    @DisplayName("Should return DOWN when JWT secret is null")
    void testHealth_WhenSecretIsNull_ReturnsDown() {
        // Arrange
        JwtHealthIndicator indicator = new JwtHealthIndicator(null);

        // Act
        Health health = indicator.health();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().containsKey("reason"));
        assertTrue(health.getDetails().containsKey("currentLength"));
        assertTrue(health.getDetails().containsKey("requiredLength"));
        assertEquals(0, health.getDetails().get("currentLength"));
        assertEquals(32, health.getDetails().get("requiredLength"));
    }

    /**
     * Test that health check returns DOWN when JWT secret is too short.
     */
    @Test
    @DisplayName("Should return DOWN when JWT secret is too short (< 32 chars)")
    void testHealth_WhenSecretIsTooShort_ReturnsDown() {
        // Arrange
        String shortSecret = "short"; // 5 characters
        JwtHealthIndicator indicator = new JwtHealthIndicator(shortSecret);

        // Act
        Health health = indicator.health();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().containsKey("reason"));
        assertTrue(health.getDetails().containsKey("currentLength"));
        assertTrue(health.getDetails().containsKey("requiredLength"));
        assertEquals(5, health.getDetails().get("currentLength"));
        assertEquals("WEAK", health.getDetails().get("strength"));
    }

    /**
     * Test that health check returns UP when JWT secret has acceptable length (32 chars).
     */
    @Test
    @DisplayName("Should return UP with ACCEPTABLE strength when secret is 32 chars")
    void testHealth_WhenSecretIsAcceptableLength_ReturnsUp() {
        // Arrange
        String acceptableSecret = "12345678901234567890123456789012"; // 32 characters
        JwtHealthIndicator indicator = new JwtHealthIndicator(acceptableSecret);

        // Act
        Health health = indicator.health();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        assertEquals("HMAC-SHA256", health.getDetails().get("algorithm"));
        assertEquals(32, health.getDetails().get("secretLength"));
        assertEquals("ACCEPTABLE", health.getDetails().get("strength"));
    }

    /**
     * Test that health check returns UP with STRONG strength when JWT secret is long (64+ chars).
     */
    @Test
    @DisplayName("Should return UP with STRONG strength when secret is 64+ chars")
    void testHealth_WhenSecretIsStrongLength_ReturnsUpWithStrong() {
        // Arrange
        String strongSecret = "1234567890123456789012345678901234567890123456789012345678901234"; // 64 characters
        JwtHealthIndicator indicator = new JwtHealthIndicator(strongSecret);

        // Act
        Health health = indicator.health();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        assertEquals("HMAC-SHA256", health.getDetails().get("algorithm"));
        assertEquals(64, health.getDetails().get("secretLength"));
        assertEquals("STRONG", health.getDetails().get("strength"));
    }

    /**
     * Test that secret bytes are calculated correctly for valid secrets.
     */
    @Test
    @DisplayName("Should correctly calculate secret bytes for UTF-8 encoding")
    void testHealth_WhenSecretIsAcceptable_CalculatesCorrectBytes() {
        // Arrange
        String validSecret = "12345678901234567890123456789012"; // 32 ASCII characters
        JwtHealthIndicator indicator = new JwtHealthIndicator(validSecret);

        // Act
        Health health = indicator.health();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        assertTrue(health.getDetails().containsKey("secretBytes"));
        assertEquals(32, health.getDetails().get("secretBytes"));
    }

    /**
     * Test that status DOWN is returned for weak secrets.
     */
    @Test
    @DisplayName("Should return DOWN for weak secrets (< 32 chars)")
    void testHealth_WhenSecretIsWeak_StatusIsDown() {
        // Arrange
        String weakSecret = "weak"; // 4 characters
        JwtHealthIndicator indicator = new JwtHealthIndicator(weakSecret);

        // Act
        Health health = indicator.health();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("WEAK", health.getDetails().get("strength"));
        assertFalse(health.getDetails().containsKey("secretBytes")); // secretBytes not included for weak secrets
    }
}
