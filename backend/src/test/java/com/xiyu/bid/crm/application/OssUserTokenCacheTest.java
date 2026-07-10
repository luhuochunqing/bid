// Input: OssUserTokenCache pure-memory mode (no Redis) via no-arg constructor
// Output: verifies put/get round-trip, TTL expiry, invalidate, clear, null/blank safety, overwrite
// Pos: crm/application - unit test for OSS user token cache (CO-152 webhook 回调用)
package com.xiyu.bid.crm.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * {@link OssUserTokenCache} 单元测试（CO-152）。
 * <p>使用无参构造 {@code new OssUserTokenCache()} 走纯内存模式，覆盖以下场景：
 * <ol>
 *   <li>put + get 基本流程</li>
 *   <li>TTL 过期返回 empty</li>
 *   <li>invalidate 清除指定用户</li>
 *   <li>clear 清空所有</li>
 *   <li>null/blank username 安全性（不抛异常）</li>
 *   <li>未缓存的 username get 返回 empty</li>
 *   <li>覆盖写入返回最新值</li>
 * </ol>
 */
class OssUserTokenCacheTest {

    // ===== 场景 1: put + get 基本流程 =====

    @Test
    @DisplayName("put + get 基本流程：存入 token 后能正确读取")
    void putAndGetBasicFlow() {
        OssUserTokenCache cache = new OssUserTokenCache();

        cache.put("user1", "oss-token-abc", 3600);

        Optional<String> token = cache.get("user1");
        assertThat(token).isPresent();
        assertThat(token.get()).isEqualTo("oss-token-abc");
    }

    // ===== 场景 2: TTL 过期 =====

    @Test
    @DisplayName("TTL 过期：存入已过期 token 后 get 返回 empty 并清理条目")
    void get_expiredEntry_returnsEmpty() {
        OssUserTokenCache cache = new OssUserTokenCache();
        // expiresInSeconds = -1 → expiresAt = now - 1s（已过期）
        // 用负值确保过期，避免 Instant 精度问题导致 flaky
        cache.put("expired-user", "oss-token-expired", -1);

        // 第一次 get：命中但已过期 → 清理并返回 empty
        assertThat(cache.get("expired-user")).isEmpty();
        // 第二次 get：条目已被清理 → 返回 empty
        assertThat(cache.get("expired-user")).isEmpty();
    }

    // ===== 场景 3: invalidate 清除 =====

    @Test
    @DisplayName("invalidate 清除：存入后调 invalidate，再 get 返回 empty")
    void invalidate_clearsEntry() {
        OssUserTokenCache cache = new OssUserTokenCache();
        cache.put("user2", "oss-token-def", 3600);
        assertThat(cache.get("user2")).isPresent();

        cache.invalidate("user2");

        assertThat(cache.get("user2")).isEmpty();
    }

    // ===== 场景 4: clear 清空所有 =====

    @Test
    @DisplayName("clear 清空所有：多个用户 token 全部清除")
    void clear_removesAllEntries() {
        OssUserTokenCache cache = new OssUserTokenCache();
        cache.put("userA", "token-a", 3600);
        cache.put("userB", "token-b", 3600);
        cache.put("userC", "token-c", 3600);
        assertThat(cache.get("userA")).isPresent();
        assertThat(cache.get("userB")).isPresent();
        assertThat(cache.get("userC")).isPresent();

        cache.clear();

        assertThat(cache.get("userA")).isEmpty();
        assertThat(cache.get("userB")).isEmpty();
        assertThat(cache.get("userC")).isEmpty();
    }

    // ===== 场景 5: null/blank username 安全性 =====

    @Test
    @DisplayName("null/blank username 安全性：put/get/invalidate 均不抛异常")
    void nullAndBlankUsername_safeNoException() {
        OssUserTokenCache cache = new OssUserTokenCache();

        // null username：put/get/invalidate 都不抛异常
        assertThatNoException().isThrownBy(() -> cache.put(null, "token", 3600));
        assertThatNoException().isThrownBy(() -> cache.get(null));
        assertThat(cache.get(null)).isEmpty();
        assertThatNoException().isThrownBy(() -> cache.invalidate(null));

        // blank username（空字符串 / 纯空格）：put/get/invalidate 都不抛异常
        assertThatNoException().isThrownBy(() -> cache.put("", "token", 3600));
        assertThatNoException().isThrownBy(() -> cache.put("   ", "token", 3600));
        assertThatNoException().isThrownBy(() -> cache.get(""));
        assertThatNoException().isThrownBy(() -> cache.get("   "));
        assertThat(cache.get("")).isEmpty();
        assertThat(cache.get("   ")).isEmpty();
        assertThatNoException().isThrownBy(() -> cache.invalidate(""));
        assertThatNoException().isThrownBy(() -> cache.invalidate("   "));
    }

    // ===== 场景 6: 未缓存的 username get =====

    @Test
    @DisplayName("未缓存的 username get：返回 empty")
    void get_uncachedUsername_returnsEmpty() {
        OssUserTokenCache cache = new OssUserTokenCache();

        Optional<String> token = cache.get("never-cached");

        assertThat(token).isEmpty();
    }

    // ===== 场景 7: 覆盖写入 =====

    @Test
    @DisplayName("覆盖写入：同一 username 多次 put，get 返回最新值")
    void put_overwrite_returnsLatest() {
        OssUserTokenCache cache = new OssUserTokenCache();

        cache.put("user3", "token-v1", 3600);
        assertThat(cache.get("user3")).hasValue("token-v1");

        cache.put("user3", "token-v2", 3600);
        assertThat(cache.get("user3")).hasValue("token-v2");

        cache.put("user3", "token-v3", 3600);
        assertThat(cache.get("user3")).hasValue("token-v3");
    }
}
