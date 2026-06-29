// Input: UserRepository.searchActiveUsers stub
// Output: UserSearchService contract (empty-on-blank, safe-limit, DTO projection only)
// Pos: Test/用户搜索服务门禁
package com.xiyu.bid.mention.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.mention.dto.UserSearchResult;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserSearchService — read-only autocompletion")
class UserSearchServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserSearchService service;

    @BeforeEach
    void setUp() {
        service = new UserSearchService(userRepository);
    }

    @Test
    @DisplayName("blank query returns empty list without hitting repository")
    void blankQuery_ReturnsEmptyList() {
        List<UserSearchResult> results = service.search("   ", 10);

        assertThat(results).isEmpty();
        verify(userRepository, never()).searchActiveUsers(anyString(), anyInt());
    }

    @Test
    @DisplayName("null query returns empty list")
    void nullQuery_ReturnsEmptyList() {
        List<UserSearchResult> results = service.search(null, 10);

        assertThat(results).isEmpty();
        verify(userRepository, never()).searchActiveUsers(anyString(), anyInt());
    }

    @Test
    @DisplayName("normal query maps to UserSearchResult and passes safe limit")
    void normalQuery_MapsResultsAndPassesLimit() {
        User alice = User.builder()
            .id(3L).username("alice").email("a@x.com").password("p")
            .fullName("Alice Smith").role(User.Role.MANAGER).build();
        User bob = User.builder()
            .id(4L).username("bob").email("b@x.com").password("p")
            .fullName("Bob Lee").role(User.Role.MANAGER).build();
        when(userRepository.searchActiveUsers("ali", 5)).thenReturn(List.of(alice, bob));

        List<UserSearchResult> results = service.search("ali", 5);

        assertThat(results).hasSize(2);
        assertThat(results.get(0)).isEqualTo(new UserSearchResult(3L, "Alice Smith", "alice", "MANAGER", null, "manager", null, "a@x.com"));
        assertThat(results.get(1)).isEqualTo(new UserSearchResult(4L, "Bob Lee", "bob", "MANAGER", null, "manager", null, "b@x.com"));
    }

    @Test
    @DisplayName("null limit falls back to default (10)")
    void nullLimit_UsesDefault() {
        when(userRepository.searchActiveUsers(anyString(), anyInt())).thenReturn(List.of());

        service.search("x", null);

        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(userRepository).searchActiveUsers(anyString(), limitCaptor.capture());
        assertThat(limitCaptor.getValue()).isEqualTo(10);
    }

    @Test
    @DisplayName("oversize limit is clamped to MAX_LIMIT (50)")
    void oversizeLimit_IsClamped() {
        when(userRepository.searchActiveUsers(anyString(), anyInt())).thenReturn(List.of());

        service.search("x", 9999);

        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(userRepository).searchActiveUsers(anyString(), limitCaptor.capture());
        assertThat(limitCaptor.getValue()).isEqualTo(50);
    }

    @Test
    @DisplayName("result contains no email or password (DTO projection only)")
    void result_DoesNotLeakSensitiveFields() {
        User alice = User.builder()
            .id(3L).username("alice").email("secret@x.com").password("hashed")
            .fullName("Alice").role(User.Role.ADMIN).build();
        when(userRepository.searchActiveUsers(anyString(), anyInt())).thenReturn(List.of(alice));

        List<UserSearchResult> results = service.search("alice", 10);

        UserSearchResult r = results.get(0);
        // UserSearchResult record exposes only id/name/role. This is a structural check.
        assertThat(r.id()).isEqualTo(3L);
        assertThat(r.name()).isEqualTo("Alice");
        assertThat(r.role()).isEqualTo("ADMIN");
        assertThat(r.roleCode()).isEqualTo("admin");
        // Record exposes search-list display fields + phone/email (CO-392 联系人联动回填).
        // password 绝不能出现在 record 字段中。
        assertThat(UserSearchResult.class.getRecordComponents()).hasSize(8);
        long leaky = IntStream.range(0, UserSearchResult.class.getRecordComponents().length)
            .filter(i -> {
                String n = UserSearchResult.class.getRecordComponents()[i].getName();
                return n.equalsIgnoreCase("password");
            }).count();
        assertThat(leaky).isZero();
    }

    @Test
    @DisplayName("employee number is preserved in search results")
    void employeeNumber_IsPreserved() {
        User user = User.builder()
            .id(6L).username("zhangsan").email("z@x.com").password("p")
            .fullName("张三").employeeNumber("E006").role(User.Role.MANAGER).build();
        when(userRepository.searchActiveUsers(anyString(), anyInt())).thenReturn(List.of(user));

        List<UserSearchResult> results = service.search("张", 10);

        assertThat(results.get(0).employeeNumber()).isEqualTo("E006");
    }

    @Test
    @DisplayName("username is used as employee number fallback for organization synced users")
    void username_IsEmployeeNumberFallback() {
        User user = User.builder()
            .id(7L).username("03645").email("u@x.com").password("p")
            .fullName("李四").employeeNumber(" ").role(User.Role.MANAGER).build();
        when(userRepository.searchActiveUsers(anyString(), anyInt())).thenReturn(List.of(user));

        List<UserSearchResult> results = service.search("03645", 10);

        assertThat(results.get(0).employeeNumber()).isEqualTo("03645");
    }

    @Test
    @DisplayName("null role maps to null role string without NPE")
    void nullRole_MapsToNullString() {
        User user = User.builder()
            .id(5L).username("x").email("x@x.com").password("p")
            .fullName("X").role(null).build();
        when(userRepository.searchActiveUsers(anyString(), anyInt())).thenReturn(List.of(user));

        List<UserSearchResult> results = service.search("x", 10);

        assertThat(results.get(0).role()).isNull();
        assertThat(results.get(0).roleCode()).isEqualTo("manager");
    }
}
