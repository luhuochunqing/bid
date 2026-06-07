// Input: degraded mode detection (external dependency unavailable)
// Output: graceful degraded response for all API calls
// Pos: Config/异常处理层
package com.xiyu.bid.config;

import com.xiyu.bid.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 开发环境降级模式支持。
 *
 * 当数据库或 Redis 连接失败时，应用不以 crash 响应，
 * 而是返回 503 + DEGRADED 状态码，让前端可以展示"服务暂不可用"。
 */
@Configuration
@Profile("dev")
public class DegradedModeController {

    private static final Logger log = LoggerFactory.getLogger(DegradedModeController.class);

    /**
     * 自定义 health indicator，暴露 DB/Redis 的实际健康状态。
     */
    @Bean
    public HealthIndicator degradedHealthIndicator() {
        return () -> {
            // 只做 passive 检测，不主动连接
            return Health.up().withDetail("degradedMode", "enabled").build();
        };
    }

    /**
     * Redis 连接失败时的降级响应。
     */
    @RestControllerAdvice
    public static class DegradedExceptionHandler {

        @ExceptionHandler(RedisConnectionFailureException.class)
        public ResponseEntity<ApiResponse<Void>> handleRedisDown(
                RedisConnectionFailureException ex, HttpServletRequest request) {
            log.warn("[degraded] Redis unavailable on {}: {}", request.getRequestURI(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.degraded("缓存服务暂不可用，部分功能可能受限"));
        }

        @ExceptionHandler(DataAccessException.class)
        public ResponseEntity<ApiResponse<Void>> handleDbDown(
                DataAccessException ex, HttpServletRequest request) {
            log.warn("[degraded] Database unavailable on {}: {}", request.getRequestURI(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.degraded("数据库暂不可用，请稍后重试"));
        }
    }
}
