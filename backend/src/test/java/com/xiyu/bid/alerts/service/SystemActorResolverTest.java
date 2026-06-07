// Input: UserRepository（admin/ADMIN 优先匹配）
// Output: SystemActorResolver 单测：缓存行为、admin 兜底、ADMIN 回退
// Pos: test/java/.../alerts/service - 纯工具单测
// 维护声明: 覆盖 admin 命中 / 回退 / 异常 / 缓存复用 四类路径.
package com.xiyu.bid.alerts.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemActorResolverTest {

    @Mock private UserRepository userRepository;

    private SystemActorResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SystemActorResolver(userRepository);
    }

    @Test
    @DisplayName("admin 用户命中：返回 admin id，ADMIN 回退不再调用")
    void resolve_AdminUserPresent_ShouldPreferAdminUsername() {
        when(userRepository.findFirstByUsernameAndEnabledTrue("admin"))
                .thenReturn(Optional.of(user(7L)));

        Long actor = resolver.resolve();

        assertThat(actor).isEqualTo(7L);
        verify(userRepository, never()).findFirstByRoleAndEnabledTrueOrderByIdAsc(User.Role.ADMIN);
    }

    @Test
    @DisplayName("admin 缺失时回退到最早 ADMIN 角色")
    void resolve_AdminMissing_ShouldFallbackToAdminRole() {
        when(userRepository.findFirstByUsernameAndEnabledTrue("admin"))
                .thenReturn(Optional.empty());
        when(userRepository.findFirstByRoleAndEnabledTrueOrderByIdAsc(User.Role.ADMIN))
                .thenReturn(Optional.of(user(13L)));

        Long actor = resolver.resolve();

        assertThat(actor).isEqualTo(13L);
    }

    @Test
    @DisplayName("无任何启用用户：返回 null（不抛异常）")
    void resolve_NothingEnabled_ShouldReturnNull() {
        when(userRepository.findFirstByUsernameAndEnabledTrue("admin"))
                .thenReturn(Optional.empty());
        when(userRepository.findFirstByRoleAndEnabledTrueOrderByIdAsc(User.Role.ADMIN))
                .thenReturn(Optional.empty());

        assertThat(resolver.resolve()).isNull();
    }

    @Test
    @DisplayName("resolveCached：二次调用应走缓存，不再查 DB")
    void resolveCached_RepeatCalls_ShouldHitCache() {
        when(userRepository.findFirstByUsernameAndEnabledTrue("admin"))
                .thenReturn(Optional.of(user(2L)));

        Long first = resolver.resolveCached();
        Long second = resolver.resolveCached();

        assertThat(first).isEqualTo(2L);
        assertThat(second).isEqualTo(2L);
        verify(userRepository, times(1)).findFirstByUsernameAndEnabledTrue("admin");
    }

    private User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setEnabled(true);
        return u;
    }
}
