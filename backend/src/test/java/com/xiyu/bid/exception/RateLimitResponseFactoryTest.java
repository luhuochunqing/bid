package com.xiyu.bid.exception;

import com.xiyu.bid.dto.ApiResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RateLimitResponseFactory} 纯核心映射测试。
 *
 * <p>验证点：
 * <ul>
 *   <li>限流元数据映射为统一 {@link ApiResponse} 格式</li>
 *   <li>响应 code 为 429</li>
 *   <li>msg 为中文友好文案，不再使用英文技术提示</li>
 *   <li>data 携带 retryAfter 秒数，便于前端显示具体等待时间</li>
 * </ul>
 */
class RateLimitResponseFactoryTest {

    @Test
    void shouldMapRateLimitMetadataToApiResponse() {
        // given
        int retryAfterSeconds = 5;

        // when
        ApiResponse<Integer> response = RateLimitResponseFactory.build(retryAfterSeconds);

        // then
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertEquals(429, response.getCode());
        assertEquals("操作太快了，请等待 5 秒后再试", response.getMsg());
        assertEquals(5, response.getData());
    }

    @Test
    void shouldUseFriendlyChineseMessageInsteadOfEnglishError() {
        // when
        ApiResponse<Integer> response = RateLimitResponseFactory.build(3);

        // then
        assertNotNull(response.getMsg());
        assertTrue(response.getMsg().contains("操作太快了"));
        assertFalse(response.getMsg().contains("Too many requests"));
        assertFalse(response.getMsg().contains("rate_limit_exceeded"));
    }

    @Test
    void shouldReturnDefaultMessageWhenRetryAfterIsZero() {
        // when
        ApiResponse<Integer> response = RateLimitResponseFactory.build(0);

        // then
        assertEquals("操作太快了，请稍等几秒再试", response.getMsg());
        assertNull(response.getData());
    }
}
