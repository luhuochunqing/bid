package com.xiyu.bid.admin.service;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import com.xiyu.bid.security.domain.EffectiveRoleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link DataScopeRoleProfileResolver} 直接单元测试。
 * <p>
 * §78 修复 5 从 DataScopeConfigService 拆出，核心职责：
 * EffectiveRoleResolver（缓存优先）→ DB 查询 → catalog fallback / unregistered。
 * <p>
 * 覆盖 fail-closed、DB 命中、catalog fallback、未注册 placeholder 四条路径。
 */
@ExtendWith(MockitoExtension.class)
class DataScopeRoleProfileResolverTest {

    @Mock
    private RoleProfileRepository roleProfileRepository;

    @Mock
    private EffectiveRoleResolver effectiveRoleResolver;

    private DataScopeRoleProfileResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DataScopeRoleProfileResolver(roleProfileRepository, effectiveRoleResolver);
    }

    @Test
    @DisplayName("null 用户返回 unregistered placeholder：dataScope=self, menuPermissions 为空")
    void resolve_nullUser_returnsUnregisteredPlaceholder() {
        RoleProfile result = resolver.resolve(null);

        assertThat(result).isNotNull();
        assertThat(result.getCode()).isNull();
        assertThat(result.getDataScope()).isEqualTo("self");
        assertThat(result.getMenuPermissions()).isEmpty();
    }

    @Test
    @DisplayName("OSS 用户缓存 admin（被污染）→ OSS_ADMIN_REJECTED → roleCode=null → unregistered placeholder")
    void resolve_ossAdminRejected_returnsUnregisteredPlaceholder() {
        User ossUser = User.builder()
                .id(1L)
                .username("09118")
                .fullName("覃超颖")
                .externalOrgSourceApp("oss")
                .enabled(true)
                .build();
        when(effectiveRoleResolver.resolve(ossUser))
                .thenReturn(new EffectiveRoleResult(null, EffectiveRoleResult.Source.OSS_ADMIN_REJECTED));

        RoleProfile result = resolver.resolve(ossUser);

        assertThat(result.getCode()).isNull();
        assertThat(result.getDataScope()).isEqualTo("self");
        assertThat(result.getMenuPermissions()).isEmpty();
    }

    @Test
    @DisplayName("OSS 用户 cache miss → CACHE_MISS_FAIL_CLOSED → roleCode=null → unregistered placeholder")
    void resolve_ossCacheMiss_returnsUnregisteredPlaceholder() {
        User ossUser = User.builder()
                .id(2L)
                .username("oss-miss")
                .externalOrgSourceApp("oss")
                .enabled(true)
                .build();
        when(effectiveRoleResolver.resolve(ossUser))
                .thenReturn(new EffectiveRoleResult(null, EffectiveRoleResult.Source.CACHE_MISS_FAIL_CLOSED));

        RoleProfile result = resolver.resolve(ossUser);

        assertThat(result.getCode()).isNull();
        assertThat(result.getDataScope()).isEqualTo("self");
        assertThat(result.getMenuPermissions()).isEmpty();
    }

    @Test
    @DisplayName("DB 查询命中 → 直接返回 DB RoleProfile（含 dataScope 和 menuPermissions）")
    void resolve_dbHit_returnsDbRoleProfile() {
        User user = User.builder()
                .id(3L)
                .username("local-bid-admin")
                .fullName("本地投标管理员")
                .enabled(true)
                .build();
        RoleProfile dbProfile = RoleProfile.builder()
                .code(RoleProfileCatalog.BID_ADMIN_CODE)
                .name("投标管理员")
                .dataScope("all")
                .build();
        dbProfile.setMenuPermissions(List.of("dashboard", "settings", "bidding"));

        when(effectiveRoleResolver.resolve(user))
                .thenReturn(new EffectiveRoleResult(RoleProfileCatalog.BID_ADMIN_CODE, EffectiveRoleResult.Source.LOCAL_USER));
        when(roleProfileRepository.findByCodeIgnoreCase(RoleProfileCatalog.BID_ADMIN_CODE))
                .thenReturn(Optional.of(dbProfile));

        RoleProfile result = resolver.resolve(user);

        assertThat(result.getCode()).isEqualTo(RoleProfileCatalog.BID_ADMIN_CODE);
        assertThat(result.getDataScope()).isEqualTo("all");
        assertThat(result.getMenuPermissions()).containsExactly("dashboard", "settings", "bidding");
    }

    @Test
    @DisplayName("DB 未命中 + 已注册 roleCode → catalog fallback（dataScope 和权限来自 catalog seed）")
    void resolve_dbMiss_registeredCode_fallsBackToCatalog() {
        User user = User.builder()
                .id(4L)
                .username("local-admin")
                .fullName("本地管理员")
                .enabled(true)
                .build();

        when(effectiveRoleResolver.resolve(user))
                .thenReturn(new EffectiveRoleResult(RoleProfileCatalog.ADMIN_CODE, EffectiveRoleResult.Source.LOCAL_USER));
        when(roleProfileRepository.findByCodeIgnoreCase(RoleProfileCatalog.ADMIN_CODE))
                .thenReturn(Optional.empty());

        RoleProfile result = resolver.resolve(user);

        assertThat(result.getCode()).isEqualTo(RoleProfileCatalog.ADMIN_CODE);
        assertThat(result.getDataScope()).isEqualTo("all");
        assertThat(result.getMenuPermissions()).contains("all");
    }

    @Test
    @DisplayName("DB 未命中 + 未注册 roleCode → unregistered placeholder（dataScope=self, 空权限）")
    void resolve_dbMiss_unregisteredCode_returnsUnregisteredPlaceholder() {
        User user = User.builder()
                .id(5L)
                .username("vendor")
                .fullName("外部供应商")
                .enabled(true)
                .build();
        String unknownCode = "vendor-contractor";

        when(effectiveRoleResolver.resolve(user))
                .thenReturn(new EffectiveRoleResult(unknownCode, EffectiveRoleResult.Source.LOCAL_USER));
        when(roleProfileRepository.findByCodeIgnoreCase(unknownCode))
                .thenReturn(Optional.empty());

        RoleProfile result = resolver.resolve(user);

        assertThat(result.getCode()).isEqualTo(unknownCode);
        assertThat(result.getDataScope()).isEqualTo("self");
        assertThat(result.getMenuPermissions()).isEmpty();
        assertThat(result.getIsSystem()).isFalse();
    }

    @Test
    @DisplayName("本地 bid-Team 用户：DB 未命中 + 已注册 roleCode → catalog fallback dataScope=self")
    void resolve_dbMiss_bidTeam_fallsBackToCatalogWithSelfScope() {
        User user = User.builder()
                .id(6L)
                .username("specialist")
                .fullName("投标专员")
                .enabled(true)
                .build();

        when(effectiveRoleResolver.resolve(user))
                .thenReturn(new EffectiveRoleResult(RoleProfileCatalog.BID_SPECIALIST_CODE, EffectiveRoleResult.Source.LOCAL_USER));
        when(roleProfileRepository.findByCodeIgnoreCase(RoleProfileCatalog.BID_SPECIALIST_CODE))
                .thenReturn(Optional.empty());

        RoleProfile result = resolver.resolve(user);

        assertThat(result.getCode()).isEqualTo(RoleProfileCatalog.BID_SPECIALIST_CODE);
        assertThat(result.getDataScope()).isEqualTo("self");
        assertThat(result.getMenuPermissions()).contains("dashboard", "bidding", "project");
    }

    @Test
    @DisplayName("OSS 用户缓存命中 bid-SystemAdmin → DB 命中 → 返回 DB profile（dataScope=all）")
    void resolve_ossCacheHit_bidSystemAdmin_returnsDbProfile() {
        User ossUser = User.builder()
                .id(7L)
                .username("09118")
                .fullName("覃超颖")
                .externalOrgSourceApp("oss")
                .enabled(true)
                .build();
        RoleProfile dbProfile = RoleProfile.builder()
                .code(RoleProfileCatalog.BID_SYSTEM_ADMIN_CODE)
                .name("投标系统管理员")
                .dataScope("all")
                .build();
        dbProfile.setMenuPermissions(List.of("dashboard", "settings", "bidding", "project"));

        when(effectiveRoleResolver.resolve(ossUser))
                .thenReturn(new EffectiveRoleResult(RoleProfileCatalog.BID_SYSTEM_ADMIN_CODE, EffectiveRoleResult.Source.CACHE_HIT));
        when(roleProfileRepository.findByCodeIgnoreCase(RoleProfileCatalog.BID_SYSTEM_ADMIN_CODE))
                .thenReturn(Optional.of(dbProfile));

        RoleProfile result = resolver.resolve(ossUser);

        assertThat(result.getCode()).isEqualTo(RoleProfileCatalog.BID_SYSTEM_ADMIN_CODE);
        assertThat(result.getDataScope()).isEqualTo("all");
    }
}
