package com.xiyu.bid.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OAuthStateService 单元测试。
 * <p>
 * 覆盖三类 state 的校验与生命周期：
 * <ol>
 *   <li>普通 state（按钮登录场景）：10 分钟 TTL，一次性删除</li>
 *   <li>{@code msg:} 前缀 state（消息推送场景）：7 天 TTL，只验证不删除</li>
 *   <li>{@code entry:} 前缀 state（企微工作台应用主页入口）：固定值，直接通过</li>
 * </ol>
 */
@DisplayName("OAuthStateService — CSRF state 生命周期管理")
class OAuthStateServiceTest {

    @Nested
    @DisplayName("普通 state（按钮登录场景）")
    class NormalStateTest {

        private StringRedisTemplate redisTemplate;
        private ValueOperations<String, String> valueOps;
        private OAuthStateService service;

        @BeforeEach
        void setUp() {
            redisTemplate = mock(StringRedisTemplate.class);
            valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            service = new OAuthStateService(redisTemplate);
        }

        @Test
        @DisplayName("storeState → 写入 Redis 并设置 10 分钟 TTL")
        void storeState_shouldStoreInRedisWith10MinTtl() {
            String state = "abc123";
            service.storeState(state);
            verify(valueOps).set(eq("oauth_state:" + state), eq("true"), eq(Duration.ofMinutes(10)));
        }

        @Test
        @DisplayName("validateAndRemoveState → 验证通过后从 Redis 删除（一次性）")
        void validateAndRemoveState_shouldDeleteAfterValidation() {
            String state = "abc123";
            when(redisTemplate.delete("oauth_state:" + state)).thenReturn(true);

            boolean result = service.validateAndRemoveState(state);

            assertThat(result).isTrue();
            verify(redisTemplate).delete("oauth_state:" + state);
        }

        @Test
        @DisplayName("validateAndRemoveState → state 不存在时返回 false")
        void validateAndRemoveState_shouldReturnFalseWhenStateNotFound() {
            String state = "nonexistent";
            when(redisTemplate.delete("oauth_state:" + state)).thenReturn(false);

            boolean result = service.validateAndRemoveState(state);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("validateAndRemoveState → 第二次验证应返回 false（已删除）")
        void validateAndRemoveState_shouldReturnFalseOnSecondValidation() {
            String state = "abc123";
            when(redisTemplate.delete("oauth_state:" + state))
                    .thenReturn(true)
                    .thenReturn(false);

            boolean first = service.validateAndRemoveState(state);
            boolean second = service.validateAndRemoveState(state);

            assertThat(first).isTrue();
            assertThat(second).isFalse();
        }
    }

    @Nested
    @DisplayName("msg: 前缀 state（消息推送场景）")
    class MessageStateTest {

        private StringRedisTemplate redisTemplate;
        private ValueOperations<String, String> valueOps;
        private OAuthStateService service;

        @BeforeEach
        void setUp() {
            redisTemplate = mock(StringRedisTemplate.class);
            valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            service = new OAuthStateService(redisTemplate);
        }

        @Test
        @DisplayName("storeStateForMessage → 生成 msg: 前缀 state 并设置 7 天 TTL")
        void storeStateForMessage_shouldGenerateMsgPrefixStateWith7DayTtl() {
            String state = service.storeStateForMessage();

            assertThat(state).startsWith("msg:");
            verify(valueOps).set(eq("oauth_state:" + state), eq("message"), eq(Duration.ofDays(7)));
        }

        @Test
        @DisplayName("validateAndRemoveState → msg: state 只验证不删除（允许多次点击）")
        void validateAndRemoveState_shouldValidateWithoutDeleteForMsgPrefix() {
            String state = "msg:abc123";
            when(redisTemplate.hasKey("oauth_state:" + state)).thenReturn(true);

            boolean first = service.validateAndRemoveState(state);
            boolean second = service.validateAndRemoveState(state);

            assertThat(first).isTrue();
            assertThat(second).isTrue();
            verify(redisTemplate, never()).delete(anyString());
            verify(redisTemplate, times(2)).hasKey("oauth_state:" + state);
        }
    }

    @Nested
    @DisplayName("entry: 前缀 state（企微工作台应用主页入口）")
    class WorkbenchEntryStateTest {

        private StringRedisTemplate redisTemplate;
        private ValueOperations<String, String> valueOps;
        private OAuthStateService service;

        @BeforeEach
        void setUp() {
            redisTemplate = mock(StringRedisTemplate.class);
            valueOps = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            service = new OAuthStateService(redisTemplate);
        }

        @Test
        @DisplayName("validateAndRemoveState → entry: 前缀 state 直接返回 true")
        void validateAndRemoveState_shouldReturnTrueForWorkbenchEntryPrefix() {
            String state = "entry:workbench";

            boolean result = service.validateAndRemoveState(state);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("validateAndRemoveState → entry: state 不读写 Redis（固定值）")
        void validateAndRemoveState_shouldNotAccessRedisForWorkbenchEntry() {
            String state = "entry:workbench";

            service.validateAndRemoveState(state);

            verify(redisTemplate, never()).delete(anyString());
            verify(redisTemplate, never()).hasKey(anyString());
            verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("validateAndRemoveState → entry: state 可重复使用（多次调用都返回 true）")
        void validateAndRemoveState_shouldBeReusableForWorkbenchEntry() {
            String state = "entry:workbench";

            boolean first = service.validateAndRemoveState(state);
            boolean second = service.validateAndRemoveState(state);
            boolean third = service.validateAndRemoveState(state);

            assertThat(first).isTrue();
            assertThat(second).isTrue();
            assertThat(third).isTrue();
        }

        @Test
        @DisplayName("validateAndRemoveState → 支持不同的 entry: 子值（如 entry:dashboard）")
        void validateAndRemoveState_shouldSupportDifferentWorkbenchEntries() {
            assertThat(service.validateAndRemoveState("entry:workbench")).isTrue();
            assertThat(service.validateAndRemoveState("entry:dashboard")).isTrue();
            assertThat(service.validateAndRemoveState("entry:project-list")).isTrue();
        }
    }

    @Nested
    @DisplayName("边界场景")
    class EdgeCaseTest {

        private OAuthStateService serviceWithRedis;
        private StringRedisTemplate redisTemplate;

        @BeforeEach
        void setUp() {
            redisTemplate = mock(StringRedisTemplate.class);
            serviceWithRedis = new OAuthStateService(redisTemplate);
        }

        @Test
        @DisplayName("validateAndRemoveState → null state 返回 false")
        void validateAndRemoveState_shouldReturnFalseForNullState() {
            assertThat(serviceWithRedis.validateAndRemoveState(null)).isFalse();
        }

        @Test
        @DisplayName("validateAndRemoveState → 空字符串 state 返回 false")
        void validateAndRemoveState_shouldReturnFalseForEmptyState() {
            assertThat(serviceWithRedis.validateAndRemoveState("")).isFalse();
        }

        @Test
        @DisplayName("validateAndRemoveState → 纯空白 state 返回 false")
        void validateAndRemoveState_shouldReturnFalseForBlankState() {
            assertThat(serviceWithRedis.validateAndRemoveState("   ")).isFalse();
        }
    }
}
