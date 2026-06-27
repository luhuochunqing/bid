package com.xiyu.bid.security.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 纯核心 {@link EffectiveRolePolicy} 单元测试。
 *
 * <p>覆盖三条解析路径 + 边界情况。不依赖 Spring/Redis/JPA，纯函数验证。
 *
 * <p>根因背景（CO-373）：OSS 同步用户 role_id=NULL，实体回退方法返回 "manager"，
 * 导致服务层权限校验误拒。纯核心根据 OSS 缓存值与 OSS 用户标识做 fail-closed 决策。
 */
@DisplayName("EffectiveRolePolicy 角色码解析决策")
class EffectiveRolePolicyTest {

    @Nested
    @DisplayName("路径1: OSS 缓存命中 → CACHE_HIT")
    class CacheHit {

        @Test
        @DisplayName("缓存有正常角色码时返回缓存值与 CACHE_HIT")
        void returnsCachedRoleCodeWhenPresent() {
            EffectiveRoleResult result = EffectiveRolePolicy.decide(
                Optional.of("bid-Team"),
                "manager",
                true
            );
            assertThat(result.roleCode()).isEqualTo("bid-Team");
            assertThat(result.source()).isEqualTo(EffectiveRoleResult.Source.CACHE_HIT);
        }

        @Test
        @DisplayName("缓存命中时忽略实体角色码（缓存优先）")
        void ignoresEntityRoleCodeWhenCacheHit() {
            EffectiveRoleResult result = EffectiveRolePolicy.decide(
                Optional.of("bid-projectLeader"),
                "manager",
                true
            );
            assertThat(result.roleCode()).isEqualTo("bid-projectLeader");
            assertThat(result.source()).isEqualTo(EffectiveRoleResult.Source.CACHE_HIT);
        }

        @Test
        @DisplayName("非 OSS 用户但缓存命中也走 CACHE_HIT（缓存是事实源）")
        void cacheHitEvenForLocalUser() {
            EffectiveRoleResult result = EffectiveRolePolicy.decide(
                Optional.of("admin"),
                "manager",
                false
            );
            assertThat(result.roleCode()).isEqualTo("admin");
            assertThat(result.source()).isEqualTo(EffectiveRoleResult.Source.CACHE_HIT);
        }
    }

    @Nested
    @DisplayName("路径2: 非 OSS 用户缓存未命中 → LOCAL_USER")
    class LocalUser {

        @Test
        @DisplayName("非 OSS 用户缓存空时返回实体角色码与 LOCAL_USER")
        void returnsEntityRoleCodeForLocalUser() {
            EffectiveRoleResult result = EffectiveRolePolicy.decide(
                Optional.empty(),
                "bid-projectLeader",
                false
            );
            assertThat(result.roleCode()).isEqualTo("bid-projectLeader");
            assertThat(result.source()).isEqualTo(EffectiveRoleResult.Source.LOCAL_USER);
        }

        @Test
        @DisplayName("非 OSS 用户缓存空且实体角色码为 manager 也返回 manager（本地用户回退合法）")
        void returnsManagerForLocalUserWithManagerRole() {
            EffectiveRoleResult result = EffectiveRolePolicy.decide(
                Optional.empty(),
                "manager",
                false
            );
            assertThat(result.roleCode()).isEqualTo("manager");
            assertThat(result.source()).isEqualTo(EffectiveRoleResult.Source.LOCAL_USER);
        }

        @Test
        @DisplayName("非 OSS 用户实体角色码为 null 时返回 null 与 LOCAL_USER")
        void returnsNullForLocalUserWithNullEntityRoleCode() {
            EffectiveRoleResult result = EffectiveRolePolicy.decide(
                Optional.empty(),
                null,
                false
            );
            assertThat(result.roleCode()).isNull();
            assertThat(result.source()).isEqualTo(EffectiveRoleResult.Source.LOCAL_USER);
        }
    }

    @Nested
    @DisplayName("路径3: OSS 用户缓存未命中 → CACHE_MISS_FAIL_CLOSED")
    class CacheMissFailClosed {

        @Test
        @DisplayName("OSS 用户缓存空时返回 null 与 CACHE_MISS_FAIL_CLOSED（不回退 manager）")
        void returnsNullForOssUserOnCacheMiss() {
            EffectiveRoleResult result = EffectiveRolePolicy.decide(
                Optional.empty(),
                "manager",
                true
            );
            assertThat(result.roleCode()).isNull();
            assertThat(result.source()).isEqualTo(EffectiveRoleResult.Source.CACHE_MISS_FAIL_CLOSED);
        }

        @Test
        @DisplayName("OSS 用户缓存空且实体角色码非空也 fail-closed 返回 null")
        void returnsNullEvenIfEntityRoleCodePresent() {
            EffectiveRoleResult result = EffectiveRolePolicy.decide(
                Optional.empty(),
                "bid-Team",
                true
            );
            assertThat(result.roleCode()).isNull();
            assertThat(result.source()).isEqualTo(EffectiveRoleResult.Source.CACHE_MISS_FAIL_CLOSED);
        }
    }

    @Nested
    @DisplayName("边界: 空字符串缓存值归一化为未命中")
    class BlankCacheValue {

        @Test
        @DisplayName("缓存值为空字符串时视为未命中（OSS 用户 → fail-closed）")
        void emptyStringCacheTreatedAsMissForOssUser() {
            EffectiveRoleResult result = EffectiveRolePolicy.decide(
                Optional.of(""),
                "manager",
                true
            );
            assertThat(result.roleCode()).isNull();
            assertThat(result.source()).isEqualTo(EffectiveRoleResult.Source.CACHE_MISS_FAIL_CLOSED);
        }

        @Test
        @DisplayName("缓存值为纯空白字符串时视为未命中（OSS 用户 → fail-closed）")
        void blankStringCacheTreatedAsMissForOssUser() {
            EffectiveRoleResult result = EffectiveRolePolicy.decide(
                Optional.of("   "),
                "manager",
                true
            );
            assertThat(result.roleCode()).isNull();
            assertThat(result.source()).isEqualTo(EffectiveRoleResult.Source.CACHE_MISS_FAIL_CLOSED);
        }

        @Test
        @DisplayName("缓存值为空字符串时视为未命中（非 OSS 用户 → LOCAL_USER）")
        void emptyStringCacheTreatedAsMissForLocalUser() {
            EffectiveRoleResult result = EffectiveRolePolicy.decide(
                Optional.of(""),
                "admin",
                false
            );
            assertThat(result.roleCode()).isEqualTo("admin");
            assertThat(result.source()).isEqualTo(EffectiveRoleResult.Source.LOCAL_USER);
        }
    }
}
