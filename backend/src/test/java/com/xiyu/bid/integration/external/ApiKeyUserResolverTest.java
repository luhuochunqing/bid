// Input: SecurityContext 中的 principal + authentication details
// Output: API Key 属主用户 ID（null = 不可解析）
// Pos: Test/单测 — ApiKeyUserResolver（details 优先零查询 + principal 回退）
package com.xiyu.bid.integration.external;

import com.xiyu.bid.apikey.application.ApiKeyService;
import com.xiyu.bid.apikey.entity.ApiKey;
import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthConstants;
import com.xiyu.bid.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyUserResolverTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private ApiKeyUserResolver resolver;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void setAuth(String principal, Object details) {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(auth.getDetails()).thenReturn(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("details 透传属主用户名 → 直接解析，不再查询 API Key 表（零额外查询）")
    void detailsProvided_usesCreatedByWithoutApiKeyLookup() {
        setAuth("api-key:5", Map.of(ApiKeyAuthConstants.DETAILS_CREATED_BY_KEY, "crm-user"));
        when(authService.resolveUserIdByUsername("crm-user")).thenReturn(42L);

        Long userId = resolver.resolveUserId();

        assertThat(userId).isEqualTo(42L);
        verifyNoInteractions(apiKeyService);
    }

    @Test
    @DisplayName("details 缺失 → 回退到 principal 反查 API Key 表")
    void detailsMissing_fallsBackToPrincipalLookup() {
        setAuth("api-key:5", null);
        ApiKey key = mock(ApiKey.class);
        when(key.getCreatedBy()).thenReturn("crm-user");
        when(apiKeyService.findById(5L)).thenReturn(Optional.of(key));
        when(authService.resolveUserIdByUsername("crm-user")).thenReturn(42L);

        Long userId = resolver.resolveUserId();

        assertThat(userId).isEqualTo(42L);
    }

    @Test
    @DisplayName("details 为空 Map（无属主键）→ 回退到 principal 反查")
    void detailsWithoutKey_fallsBack() {
        setAuth("api-key:5", Map.of("other", "value"));
        ApiKey key = mock(ApiKey.class);
        when(key.getCreatedBy()).thenReturn("crm-user");
        when(apiKeyService.findById(5L)).thenReturn(Optional.of(key));
        when(authService.resolveUserIdByUsername("crm-user")).thenReturn(42L);

        assertThat(resolver.resolveUserId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("非 API Key principal（JWT 用户）→ null")
    void nonApiKeyPrincipal_returnsNull() {
        setAuth("some-user", null);

        assertThat(resolver.resolveUserId()).isNull();
        verifyNoInteractions(apiKeyService, authService);
    }

    @Test
    @DisplayName("details 属主为空串 → null")
    void blankCreatedBy_returnsNull() {
        setAuth("api-key:5", Map.of(ApiKeyAuthConstants.DETAILS_CREATED_BY_KEY, "  "));

        assertThat(resolver.resolveUserId()).isNull();
        verifyNoInteractions(apiKeyService, authService);
    }

    @Test
    @DisplayName("回退路径：API Key 已删除 → null")
    void apiKeyNotFound_returnsNull() {
        setAuth("api-key:999", null);
        when(apiKeyService.findById(999L)).thenReturn(Optional.empty());

        assertThat(resolver.resolveUserId()).isNull();
    }

    @Test
    @DisplayName("属主用户名对应系统用户不存在 → null（不阻断主流程）")
    void usernameNotFound_returnsNull() {
        setAuth("api-key:5", Map.of(ApiKeyAuthConstants.DETAILS_CREATED_BY_KEY, "ghost"));
        when(authService.resolveUserIdByUsername("ghost"))
                .thenThrow(new UsernameNotFoundException("ghost not found"));

        assertThat(resolver.resolveUserId()).isNull();
    }

    @Test
    @DisplayName("无认证上下文 → null")
    void noAuthentication_returnsNull() {
        SecurityContextHolder.clearContext();

        assertThat(resolver.resolveUserId()).isNull();
        verifyNoInteractions(apiKeyService, authService);
    }
}
