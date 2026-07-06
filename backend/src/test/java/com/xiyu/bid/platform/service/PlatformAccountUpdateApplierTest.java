// Input: PlatformAccountUpdateApplier + mock repository/encryption
// Output: 字段应用与唯一性校验验证
// Pos: Test/纯核心验证
package com.xiyu.bid.platform.service;

import com.xiyu.bid.platform.dto.PlatformAccountCreateRequest;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.entity.PlatformAccount.PlatformType;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

/**
 * CO-522: 验证从 service 拆出的 {@link PlatformAccountUpdateApplier}。
 */
@ExtendWith(MockitoExtension.class)
class PlatformAccountUpdateApplierTest {

    @Mock
    private PlatformAccountRepository repository;

    @Mock
    private PasswordEncryptionUtil passwordEncryptionUtil;

    @InjectMocks
    private PlatformAccountUpdateApplier applier;

    @Test
    @DisplayName("applyFields: 非 null 字段覆盖原值，null 字段保留原值")
    void applyFields_overridesNonNullKeepsNull() {
        PlatformAccount account = PlatformAccount.builder()
                .id(1L).username("olduser").accountName("旧名称")
                .platformType(PlatformType.GOV_PROCUREMENT).password("enc:old").build();
        PlatformAccountCreateRequest req = PlatformAccountCreateRequest.builder()
                .username("newuser")  // 覆盖
                .password(null)        // 保留原值
                .accountName("新名称")
                .build();

        applier.applyFields(account, req);

        assertThat(account.getUsername()).isEqualTo("newuser");
        assertThat(account.getAccountName()).isEqualTo("新名称");
        assertThat(account.getPassword()).isEqualTo("enc:old");  // 未变
        assertThat(account.getPlatformType()).isEqualTo(PlatformType.GOV_PROCUREMENT);  // 未变
    }

    @Test
    @DisplayName("applyFields: 提供新密码时加密后写入")
    void applyFields_newPassword_getsEncrypted() {
        PlatformAccount account = PlatformAccount.builder()
                .id(1L).username("u").accountName("n")
                .platformType(PlatformType.GOV_PROCUREMENT).password("enc:old").build();
        PlatformAccountCreateRequest req = PlatformAccountCreateRequest.builder()
                .password("newSecret123").build();
        when(passwordEncryptionUtil.encrypt("newSecret123")).thenReturn("enc:newSecret123");

        applier.applyFields(account, req);

        assertThat(account.getPassword()).isEqualTo("enc:newSecret123");
        verify(passwordEncryptionUtil).encrypt("newSecret123");
    }

    @Test
    @DisplayName("validateUniqueness: 目标用户名已被其他账号占用抛异常")
    void validateUniqueness_duplicateUsername_throws() {
        PlatformAccount existing = PlatformAccount.builder().id(1L)
                .platformType(PlatformType.GOV_PROCUREMENT).username("olduser").build();
        PlatformAccountCreateRequest req = PlatformAccountCreateRequest.builder()
                .platformType(PlatformType.GOV_PROCUREMENT).username("taken").build();
        when(repository.findByPlatformTypeAndUsername(PlatformType.GOV_PROCUREMENT, "taken"))
                .thenReturn(Optional.of(PlatformAccount.builder().id(2L).build()));

        assertThatThrownBy(() -> applier.validateUniqueness(req, existing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already exists: taken");
    }

    @Test
    @DisplayName("validateUniqueness: 用户名未变（同账号）放行，不查 repository")
    void validateUniqueness_sameUsernameOnSameAccount_passes() {
        PlatformAccount existing = PlatformAccount.builder().id(1L)
                .platformType(PlatformType.GOV_PROCUREMENT).username("sameuser").build();
        PlatformAccountCreateRequest req = PlatformAccountCreateRequest.builder()
                .platformType(PlatformType.GOV_PROCUREMENT).username("sameuser").build();

        applier.validateUniqueness(req, existing);

        verify(repository, never()).findByPlatformTypeAndUsername(any(), any());
    }
}
