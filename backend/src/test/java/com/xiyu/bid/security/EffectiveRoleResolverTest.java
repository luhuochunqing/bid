package com.xiyu.bid.security;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.security.domain.EffectiveRoleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 外壳 {@link EffectiveRoleResolver} 单元测试。
 *
 * <p>验证：读取 OSS 缓存 → 委托纯核心 {@code EffectiveRolePolicy} → 按 source 分级记日志。
 * 缓存空字符串归一化、OSS 用户 fail-closed、本地用户回退均覆盖。
 *
 * <p>根因背景（CO-373）：此前 19 处服务直调 {@code User.getRoleCode()} 读到 "manager" 被拒；
 * resolver 是统一入口，确保所有服务层权限校验走 OSS 缓存优先。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EffectiveRoleResolver 角色码解析外壳")
class EffectiveRoleResolverTest {

    private static final Logger log = LoggerFactory.getLogger(EffectiveRoleResolverTest.class);

    @Mock
    private RoleCodeCachePort roleCodeCachePort;

    @InjectMocks
    private EffectiveRoleResolver effectiveRoleResolver;

    private User ossUser;
    private User localUser;

    @BeforeEach
    void setUp() {
        ossUser = User.builder()
            .id(1L)
            .username("00444")
            .role(com.xiyu.bid.entity.User.Role.MANAGER)
            .externalOrgSourceApp("ehsy-oss")
            .build();
        // roleProfile 为 null（OSS 用户 role_id=NULL 场景）

        localUser = User.builder()
            .id(2L)
            .username("admin01")
            .role(com.xiyu.bid.entity.User.Role.MANAGER)
            .build();
        // externalOrgSourceApp 为 null（本地用户）
    }

    @Nested
    @DisplayName("OSS 用户：缓存命中")
    class OssCacheHit {

        @Test
        @DisplayName("缓存有正确角色码时返回缓存值（bid-Team）")
        void returnsCachedRoleForOssUser() {
            when(roleCodeCachePort.getRoleCode("00444")).thenReturn(Optional.of("bid-Team"));

            String roleCode = effectiveRoleResolver.resolveRoleCode(ossUser);

            assertThat(roleCode).isEqualTo("bid-Team");
        }

        @Test
        @DisplayName("resolve 返回 CACHE_HIT 来源")
        void returnsCacheHitSource() {
            when(roleCodeCachePort.getRoleCode("00444")).thenReturn(Optional.of("bid-projectLeader"));

            EffectiveRoleResult result = effectiveRoleResolver.resolve(ossUser);

            assertThat(result.roleCode()).isEqualTo("bid-projectLeader");
            assertThat(result.source()).isEqualTo(EffectiveRoleResult.Source.CACHE_HIT);
        }

        @Test
        @DisplayName("缓存命中时不读实体角色码作回退")
        void doesNotFallbackToEntityWhenCacheHit() {
            when(roleCodeCachePort.getRoleCode("00444")).thenReturn(Optional.of("bid-Team"));

            effectiveRoleResolver.resolveRoleCode(ossUser);

            // 实体 getRoleCode() 不应被调用作回退（缓存命中直接返回）
            // 验证只调用了一次缓存查询
            verify(roleCodeCachePort, times(1)).getRoleCode("00444");
        }
    }

    @Nested
    @DisplayName("OSS 用户：缓存未命中 → fail-closed")
    class OssCacheMiss {

        @Test
        @DisplayName("缓存为空时返回 null（fail-closed，不回退 manager）")
        void returnsNullWhenCacheEmptyForOssUser() {
            when(roleCodeCachePort.getRoleCode("00444")).thenReturn(Optional.empty());

            String roleCode = effectiveRoleResolver.resolveRoleCode(ossUser);

            assertThat(roleCode).isNull();
        }

        @Test
        @DisplayName("resolve 返回 CACHE_MISS_FAIL_CLOSED 来源")
        void returnsFailClosedSource() {
            when(roleCodeCachePort.getRoleCode("00444")).thenReturn(Optional.empty());

            EffectiveRoleResult result = effectiveRoleResolver.resolve(ossUser);

            assertThat(result.roleCode()).isNull();
            assertThat(result.source()).isEqualTo(EffectiveRoleResult.Source.CACHE_MISS_FAIL_CLOSED);
        }

        @Test
        @DisplayName("缓存值为空字符串时归一化为未命中 → fail-closed")
        void emptyStringCacheNormalizedToMiss() {
            when(roleCodeCachePort.getRoleCode("00444")).thenReturn(Optional.of(""));

            String roleCode = effectiveRoleResolver.resolveRoleCode(ossUser);

            assertThat(roleCode).isNull();
        }
    }

    @Nested
    @DisplayName("本地用户：缓存未命中 → 实体角色码")
    class LocalUserFallback {

        @Test
        @DisplayName("本地用户缓存空时返回实体角色码")
        void returnsEntityRoleCodeForLocalUser() {
            when(roleCodeCachePort.getRoleCode("admin01")).thenReturn(Optional.empty());

            // localUser 的 roleProfile 为 null → getRoleCode() 回退 "manager"
            // 但本地用户这是合法回退（非 OSS 用户）
            String roleCode = effectiveRoleResolver.resolveRoleCode(localUser);

            assertThat(roleCode).isEqualTo("manager");
        }

        @Test
        @DisplayName("本地用户 resolve 返回 LOCAL_USER 来源")
        void returnsLocalUserSource() {
            when(roleCodeCachePort.getRoleCode("admin01")).thenReturn(Optional.empty());

            EffectiveRoleResult result = effectiveRoleResolver.resolve(localUser);

            assertThat(result.source()).isEqualTo(EffectiveRoleResult.Source.LOCAL_USER);
        }

        @Test
        @DisplayName("本地用户即使缓存命中也用缓存值（缓存优先于实体）")
        void localUserUsesCacheWhenHit() {
            when(roleCodeCachePort.getRoleCode("admin01")).thenReturn(Optional.of("admin"));

            String roleCode = effectiveRoleResolver.resolveRoleCode(localUser);

            assertThat(roleCode).isEqualTo("admin");
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("user 为 null 时返回 null（防御性）")
        void returnsNullWhenUserIsNull() {
            String roleCode = effectiveRoleResolver.resolveRoleCode(null);

            assertThat(roleCode).isNull();
            verify(roleCodeCachePort, never()).getRoleCode(anyString());
        }

        @Test
        @DisplayName("resolve(null) 返回 null + null source")
        void resolveNullReturnsNull() {
            EffectiveRoleResult result = effectiveRoleResolver.resolve(null);

            assertThat(result).satisfiesAnyOf(
                r -> assertThat(r).isNull(),
                r -> {
                    assertThat(r.roleCode()).isNull();
                    assertThat(r.source()).isNull();
                }
            );
        }

        @Test
        @DisplayName("空白 externalOrgSourceApp 视为本地用户")
        void blankExternalOrgSourceAppTreatedAsLocal() {
            User blankOssUser = User.builder()
                .id(3L)
                .username("blank")
                .role(com.xiyu.bid.entity.User.Role.MANAGER)
                .externalOrgSourceApp("   ")
                .build();
            when(roleCodeCachePort.getRoleCode("blank")).thenReturn(Optional.empty());

            EffectiveRoleResult result = effectiveRoleResolver.resolve(blankOssUser);

            // blank externalOrgSourceApp → isOssUser=false → LOCAL_USER
            assertThat(result.source()).isEqualTo(EffectiveRoleResult.Source.LOCAL_USER);
        }
    }
}
