// Input: 业务失败、资源缺失和参数校验异常
// Output: ApiKeyService.updateScopes 的 TDD 单元测试
// Pos: Test/单测 — API Key scopes 更新逻辑
package com.xiyu.bid.apikey.application;

import com.xiyu.bid.apikey.entity.ApiKey;
import com.xiyu.bid.apikey.entity.ApiKeyStatus;
import com.xiyu.bid.apikey.infrastructure.ApiKeyRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository repository;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private ApiKey key(Long id, String name, String scopes) {
        return ApiKey.builder()
                .id(id)
                .name(name)
                .keyHash("hash-" + id)
                .scopes(scopes)
                .status(ApiKeyStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("updateScopes 全量替换 scopes 并支持改名")
    void updateScopes_replacesScopesAndName() {
        ApiKey existing = key(1L, "旧名称", "tender:read");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = apiKeyService.updateScopes(1L, "新名称", List.of("tender:read", "ca:read"));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("新名称");
        assertThat(result.scopes()).containsExactly("tender:read", "ca:read");
        verify(repository).save(argThat(k ->
                k.getScopes().equals("tender:read,ca:read") && k.getName().equals("新名称")));
    }

    @Test
    @DisplayName("updateScopes 对重复 scopes 去重并保留首个出现顺序")
    void updateScopes_deduplicatesScopesPreservingOrder() {
        ApiKey existing = key(2L, "名称", "tender:read");
        when(repository.findById(2L)).thenReturn(Optional.of(existing));
        when(repository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = apiKeyService.updateScopes(2L, null,
                List.of("ca:read", "tender:read", "ca:read"));

        assertThat(result.scopes()).containsExactly("ca:read", "tender:read");
        verify(repository).save(argThat(k -> k.getScopes().equals("ca:read,tender:read")));
    }

    @Test
    @DisplayName("updateScopes name 为 null 或空白时保留原名")
    void updateScopes_keepsNameWhenBlank() {
        ApiKey existing = key(3L, "原名称", "tender:read");
        when(repository.findById(3L)).thenReturn(Optional.of(existing));
        when(repository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = apiKeyService.updateScopes(3L, "  ", List.of("ca:read"));

        assertThat(result.name()).isEqualTo("原名称");
        verify(repository).save(argThat(k -> k.getName().equals("原名称")));
    }

    @Test
    @DisplayName("updateScopes id 不存在 → 抛出 ResourceNotFoundException")
    void updateScopes_notFound_throws() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.updateScopes(99L, "n", List.of("ca:read")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}