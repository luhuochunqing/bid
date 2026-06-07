package com.xiyu.bid.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Authentication domain metrics.
 * Records login, logout, and token refresh events.
 */
@Component
public class AuthMetrics extends BaseMetrics {

    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter logoutCounter;
    private final Counter tokenRefreshSuccessCounter;
    private final Counter tokenRefreshFailureCounter;
    private final Timer loginTimer;
    private final ConcurrentHashMap<String, Counter> loginByUserType = new ConcurrentHashMap<>();

    public AuthMetrics(MeterRegistry registry) {
        super(registry);

        this.loginSuccessCounter = Counter.builder(PREFIX + "_auth_login_success_total")
                .description("Total successful login attempts")
                .register(registry);

        this.loginFailureCounter = Counter.builder(PREFIX + "_auth_login_failure_total")
                .description("Total failed login attempts")
                .register(registry);

        this.logoutCounter = Counter.builder(PREFIX + "_auth_logout_total")
                .description("Total logout events")
                .register(registry);

        this.tokenRefreshSuccessCounter = Counter.builder(PREFIX + "_auth_token_refresh_success_total")
                .description("Total successful token refresh attempts")
                .register(registry);

        this.tokenRefreshFailureCounter = Counter.builder(PREFIX + "_auth_token_refresh_failure_total")
                .description("Total failed token refresh attempts")
                .register(registry);

        this.loginTimer = Timer.builder(PREFIX + "_auth_login_duration_seconds")
                .description("Login request duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void recordLoginSuccess() {
        loginSuccessCounter.increment();
    }

    public void recordLoginSuccess(String userType) {
        loginSuccessCounter.increment();
        loginByUserType.computeIfAbsent(userType, ut ->
                Counter.builder(PREFIX + "_auth_login_success_total")
                        .description("Successful logins by user type")
                        .tag("user_type", ut)
                        .register(registry)
        ).increment();
    }

    public void recordLoginFailure() {
        loginFailureCounter.increment();
    }

    public void recordLogout() {
        logoutCounter.increment();
    }

    public void recordTokenRefreshSuccess() {
        tokenRefreshSuccessCounter.increment();
    }

    public void recordTokenRefreshFailure() {
        tokenRefreshFailureCounter.increment();
    }

    public void recordLoginLatency(Runnable operation) {
        loginTimer.record(operation);
    }

    public <T> T recordLoginLatency(Supplier<T> operation) {
        return loginTimer.record(operation);
    }

    public long recordLoginLatencyNanos(long startNanos) {
        return System.nanoTime() - startNanos;
    }
}
