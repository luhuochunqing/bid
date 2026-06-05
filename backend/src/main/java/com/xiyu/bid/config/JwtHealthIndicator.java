package com.xiyu.bid.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * JWT Health Indicator
 *
 * Custom health check for JWT configuration.
 * Verifies that JWT secret is properly configured and meets security requirements.
 *
 * Security requirements:
 * - Secret must not be null
 * - Secret length must be at least 32 characters (256 bits for HMAC-SHA256)
 *
 * This indicator is automatically registered with Spring Boot Actuator
 * and can be accessed via /actuator/health endpoint.
 */
@Slf4j
@Component
public class JwtHealthIndicator implements HealthIndicator {

    /**
     * Minimum recommended secret length for HMAC-SHA256
     * 32 bytes = 256 bits (recommended for JWT HS256)
     */
    private static final int MIN_SECRET_LENGTH = 32;

    /**
     * Strong secret length threshold
     * 64 bytes = 512 bits (stronger security)
     */
    private static final int STRONG_SECRET_LENGTH = 64;

    private final String jwtSecret;

    /**
     * Constructor with JWT secret injection.
     * Uses a default empty string if no secret is configured.
     */
    public JwtHealthIndicator(@org.springframework.beans.factory.annotation.Value("${jwt.secret:}") String pJwtSecret) {
        this.jwtSecret = pJwtSecret;
    }

    /**
     * Perform health check on JWT configuration.
     *
     * @return Health status with details about JWT configuration
     */
    @Override
    public Health health() {
        try {
            // Check if secret is configured
            if (jwtSecret == null) {
                log.warn("JWT health check failed: JWT secret is not configured");
                return Health.down()
                        .withDetail("reason", "JWT secret not configured")
                        .withDetail("currentLength", 0)
                        .withDetail("requiredLength", MIN_SECRET_LENGTH)
                        .withDetail("recommendation", "Set JWT_SECRET environment variable with at least " +
                                MIN_SECRET_LENGTH + " characters")
                        .build();
            }

            // Check secret length
            int secretLength = jwtSecret.length();
            byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);

            if (secretLength < MIN_SECRET_LENGTH) {
                log.warn("JWT health check degraded: JWT secret is too short ({} chars)", secretLength);
                return Health.down()
                        .withDetail("reason", "JWT secret too short for secure HMAC-SHA256")
                        .withDetail("currentLength", secretLength)
                        .withDetail("requiredLength", MIN_SECRET_LENGTH)
                        .withDetail("algorithm", "HMAC-SHA256")
                        .withDetail("strength", "WEAK")
                        .withDetail("recommendation", "Use a JWT secret with at least " + MIN_SECRET_LENGTH +
                                " characters (" + MIN_SECRET_LENGTH * 8 + " bits)")
                        .build();
            }

            // Build health status with details
            Health.Builder builder = Health.up();

            builder.withDetail("algorithm", "HMAC-SHA256");
            builder.withDetail("secretLength", secretLength);
            builder.withDetail("secretBytes", secretBytes.length);

            // Add strength assessment
            if (secretLength >= STRONG_SECRET_LENGTH) {
                builder.withDetail("strength", "STRONG");
                log.debug("JWT health check: STRONG ({} chars)", secretLength);
            } else {
                builder.withDetail("strength", "ACCEPTABLE");
                log.debug("JWT health check: ACCEPTABLE ({} chars)", secretLength);
            }

            return builder.build();

        } catch (Exception e) {
            log.error("JWT health check failed with exception", e);
            return Health.down(e)
                    .withDetail("reason", "Exception during JWT health check")
                    .build();
        }
    }
}
