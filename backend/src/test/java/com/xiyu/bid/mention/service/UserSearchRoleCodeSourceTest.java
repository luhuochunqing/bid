// Input: UserRepository + 用户 DB role_profile 数据
// Output: 验证选人接口返回的 roleCode 来自 DB role_profile 而非 OSS 缓存
// Pos: Test/spec 033 FR-B004 — 选人接口 roleCode 来源测试
//
// spec 033 方案 B 立即实施的测试安全网。
// 验证 UserSearchService.search() 和 findByIds() 返回的 roleCode 与 DB role_profile 一致。
// 当前实现直调 u.getRoleCode()（返回 roleProfile.code），测试锁定该行为，
// 并标注"应通过 DbRoleSnapshotResolver"的期望（FR-A009 迁移目标）。
package com.xiyu.bid.mention.service;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.mention.dto.UserSearchResult;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * spec 033 FR-B004：选人接口 roleCode 来源测试。
 *
 * <p>验证选人接口返回的 {@code roleCode} 与 DB {@code role_profile} 表的值一致，
 * 不读 OSS 缓存。当前实现直调 {@code u.getRoleCode()}（返回 {@code roleProfile.code}），
 * 测试锁定该行为。
 *
 * <p>当方案 A 的 FR-A009 落地后（迁移到 {@code DbRoleSnapshotResolver}），
 * 这些测试仍应通过 —— 因为 {@code DbRoleSnapshotResolver.resolveRoleCode(user)} 内部也是读取
 * {@code user.getRoleCode()}，只是多了一层封装。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("spec 033 FR-B004 — 选人接口 roleCode 来源测试")
class UserSearchRoleCodeSourceTest {

    @Mock
    private UserRepository userRepository;

    private UserSearchService service;

    @BeforeEach
    void setUp() {
        service = new UserSearchService(userRepository);
    }

    @Test
    @DisplayName("search 返回的 roleCode 与 DB role_profile.code 一致，不读 OSS 缓存")
    void searchRoleCodeShouldComeFromDbRoleProfile() {
        // Given: 用户 DB role_profile.code = "bid-Team"（即使该用户是 OSS 同步用户）
        RoleProfile roleProfile = RoleProfile.builder()
                .code("bid-Team")
                .name("投标专员")
                .build();
        User user = User.builder()
                .id(1L).username("06131").email("u@x.com").password("p")
                .fullName("王晓莉").role(User.Role.MANAGER)
                .roleProfile(roleProfile)
                .externalOrgSourceApp("OSS") // OSS 用户
                .enabled(true)
                .build();
        when(userRepository.searchActiveUsers(anyString(), anyInt())).thenReturn(List.of(user));

        // When: 搜索用户
        List<UserSearchResult> results = service.search("王", 10);

        // Then: 返回的 roleCode 是 DB role_profile.code（bid-Team），不是 OSS 缓存的可能值（如 admin）
        assertThat(results).hasSize(1);
        assertThat(results.get(0).roleCode()).isEqualTo("bid-Team");
    }

    @Test
    @DisplayName("findByIds 返回的 roleCode 与 DB role_profile.code 一致")
    void findByIdsRoleCodeShouldComeFromDbRoleProfile() {
        // Given: 多个用户，DB role_profile 各不相同
        User user1 = User.builder()
                .id(1L).username("06131").email("u1@x.com").password("p")
                .fullName("王晓莉").role(User.Role.MANAGER)
                .roleProfile(RoleProfile.builder().code("bid-Team").name("投标专员").build())
                .externalOrgSourceApp("OSS")
                .enabled(true)
                .build();
        User user2 = User.builder()
                .id(2L).username("06234").email("u2@x.com").password("p")
                .fullName("郑蓉蓉").role(User.Role.MANAGER)
                .roleProfile(RoleProfile.builder().code("/bidAdmin").name("投标管理员").build())
                .externalOrgSourceApp("OSS")
                .enabled(true)
                .build();
        when(userRepository.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(user1, user2));

        // When: 批量查询
        List<UserSearchResult> results = service.findByIds(List.of(1L, 2L));

        // Then: 每个用户的 roleCode 都来自 DB role_profile，不读 OSS 缓存
        assertThat(results).hasSize(2);
        assertThat(results.get(0).roleCode()).isEqualTo("bid-Team");
        assertThat(results.get(1).roleCode()).isEqualTo("/bidAdmin");
    }

    @Test
    @DisplayName("DB role_profile 为 null 时 roleCode fallback 到 legacy role（不读 OSS 缓存）")
    void nullRoleProfileShouldFallbackToLegacyRoleNotOssCache() {
        // Given: 用户 DB 没有 role_profile（历史遗留数据）
        User user = User.builder()
                .id(3L).username("legacy_user").email("u@x.com").password("p")
                .fullName("历史用户").role(User.Role.MANAGER)
                // roleProfile = null
                .externalOrgSourceApp("OSS")
                .enabled(true)
                .build();
        when(userRepository.searchActiveUsers(anyString(), anyInt())).thenReturn(List.of(user));

        // When: 搜索用户
        List<UserSearchResult> results = service.search("历史", 10);

        // Then: roleCode fallback 到 legacy role（manager），不读 OSS 缓存
        // 注：这是 User.getRoleCode() 的 fallback 行为，spec 033 FR-A008 Phase 2 会改为抛异常
        assertThat(results).hasSize(1);
        assertThat(results.get(0).roleCode()).isEqualTo("manager");
    }

    @Test
    @DisplayName("OSS 用户的 DB role_profile 与 OSS 缓存不一致时，返回 DB 值")
    void ossUserWithMismatchedCacheShouldReturnDbValue() {
        // Given: OSS 用户 DB role_profile.code = "bid-Team"
        // 但假设 OSS 缓存中 roleCode = "admin"（CO-391 真实场景）
        // UserSearchService 不读 OSS 缓存，应返回 DB 的 "bid-Team"
        RoleProfile roleProfile = RoleProfile.builder()
                .code("bid-Team")
                .name("投标专员")
                .build();
        User user = User.builder()
                .id(4L).username("06131").email("u@x.com").password("p")
                .fullName("王晓莉").role(User.Role.MANAGER)
                .roleProfile(roleProfile)
                .externalOrgSourceApp("OSS")
                .enabled(true)
                .build();
        when(userRepository.searchActiveUsers(anyString(), anyInt())).thenReturn(List.of(user));

        // When: 搜索用户（UserSearchService 不注入 OssPermissionCache，无法读 OSS 缓存）
        List<UserSearchResult> results = service.search("王", 10);

        // Then: 返回 DB 的 "bid-Team"，不是 OSS 缓存的 "admin"
        assertThat(results.get(0).roleCode()).isEqualTo("bid-Team");
    }
}
