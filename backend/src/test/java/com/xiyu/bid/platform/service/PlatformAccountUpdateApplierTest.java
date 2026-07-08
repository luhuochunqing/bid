// Input: PlatformAccountUpdateApplier + mock encryption
// Output: 字段应用验证
// Pos: Test/纯核心验证
package com.xiyu.bid.platform.service;

import com.xiyu.bid.platform.dto.PlatformAccountCreateRequest;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * CO-522: 验证从 service 拆出的 {@link PlatformAccountUpdateApplier}。
 */
@ExtendWith(MockitoExtension.class)
class PlatformAccountUpdateApplierTest {

    @Mock
    private PasswordEncryptionUtil passwordEncryptionUtil;

    @InjectMocks
    private PlatformAccountUpdateApplier applier;

    @Test
    @DisplayName("applyFields: 非 null 字段覆盖原值，null 字段保留原值")
    void applyFields_overridesNonNullKeepsNull() {
        PlatformAccount account = PlatformAccount.builder()
                .id(1L).username("olduser").accountName("旧名称")
                .password("enc:old").build();
        PlatformAccountCreateRequest req = PlatformAccountCreateRequest.builder()
                .username("newuser")  // 覆盖
                .password(null)        // 保留原值
                .accountName("新名称")
                .build();

        applier.applyFields(account, req);

        assertThat(account.getUsername()).isEqualTo("newuser");
        assertThat(account.getAccountName()).isEqualTo("新名称");
        assertThat(account.getPassword()).isEqualTo("enc:old");  // 未变
    }

    @Test
    @DisplayName("applyFields: 提供新密码时加密后写入")
    void applyFields_newPassword_getsEncrypted() {
        PlatformAccount account = PlatformAccount.builder()
                .id(1L).username("u").accountName("n")
                .password("enc:old").build();
        PlatformAccountCreateRequest req = PlatformAccountCreateRequest.builder()
                .password("newSecret123").build();
        when(passwordEncryptionUtil.encrypt("newSecret123")).thenReturn("enc:newSecret123");

        applier.applyFields(account, req);

        assertThat(account.getPassword()).isEqualTo("enc:newSecret123");
        verify(passwordEncryptionUtil).encrypt("newSecret123");
    }
}
