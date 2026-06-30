package com.xiyu.bid.resources.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.repository.CaCertificatePlatformRepository;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-409: CA 信息管理模块投标专员操作项权限矩阵对齐.
 *
 * <p>复刻 {@code PlatformAccountServiceTest} 的保管员差异化校验范式（lessons §28）：
 * Controller 放宽类级 @PreAuthorize 后，deactivate 的细粒度权限校验下沉到 Service 层。
 * 管理员可下架任意 CA；投标专员仅可下架自己保管的 CA；非保管员抛 AccessDeniedException。
 */
@ExtendWith(MockitoExtension.class)
class CaCertificateServiceTest {

    @Mock
    private CaCertificateRepository certificateRepository;
    @Mock
    private CaCertificatePlatformRepository platformLinkRepository;
    @Mock
    private PasswordEncryptionUtil passwordEncryptionUtil;
    @Mock
    private EffectiveRoleResolver effectiveRoleResolver;
    @Mock
    private UserRepository userRepository;

    // ── deactivate 保管员差异化校验 ──

    @Test
    void deactivate_admin_canDeactivateAnyCaCertificate() {
        CaCertificateService service = newService();
        User admin = user(10L, "admin");
        CaCertificateEntity ca = caCertificate(1L, 99L); // 保管员是别人
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(effectiveRoleResolver.resolveRoleCode(admin)).thenReturn("admin");
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(ca));

        assertThatCode(() -> service.deactivate(1L, userDetails("admin"))).doesNotThrowAnyException();

        verify(certificateRepository).save(ca);
    }

    @Test
    void deactivate_bidTeamCustodian_canDeactivateOwnCaCertificate() {
        CaCertificateService service = newService();
        User bidTeam = user(20L, "xiaowang");
        CaCertificateEntity ca = caCertificate(1L, 20L); // 自己是保管员
        when(userRepository.findByUsername("xiaowang")).thenReturn(Optional.of(bidTeam));
        when(effectiveRoleResolver.resolveRoleCode(bidTeam)).thenReturn("bid-Team");
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(ca));

        assertThatCode(() -> service.deactivate(1L, userDetails("xiaowang"))).doesNotThrowAnyException();

        verify(certificateRepository).save(ca);
    }

    @Test
    void deactivate_bidTeamNotCustodian_throwsAccessDenied() {
        CaCertificateService service = newService();
        User bidTeam = user(20L, "xiaowang");
        CaCertificateEntity ca = caCertificate(1L, 99L); // 保管员是别人
        when(userRepository.findByUsername("xiaowang")).thenReturn(Optional.of(bidTeam));
        when(effectiveRoleResolver.resolveRoleCode(bidTeam)).thenReturn("bid-Team");
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(ca));

        assertThatThrownBy(() -> service.deactivate(1L, userDetails("xiaowang")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("保管员");

        verify(certificateRepository, never()).save(any());
    }

    @Test
    void deactivate_nullCurrentUser_throwsAccessDenied() {
        CaCertificateService service = newService();

        assertThatThrownBy(() -> service.deactivate(1L, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(certificateRepository, never()).save(any());
    }

    // ── helpers ──

    private CaCertificateService newService() {
        return new CaCertificateService(
                certificateRepository,
                platformLinkRepository,
                passwordEncryptionUtil,
                effectiveRoleResolver,
                userRepository
        );
    }

    private User user(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        return u;
    }

    private UserDetails userDetails(String username) {
        UserDetails ud = org.mockito.Mockito.mock(UserDetails.class);
        when(ud.getUsername()).thenReturn(username);
        return ud;
    }

    private CaCertificateEntity caCertificate(Long id, Long custodianId) {
        return CaCertificateEntity.builder()
                .id(id)
                .caType("ENTITY_CA")
                .sealType("OFFICIAL_SEAL")
                .expiryDate(java.time.LocalDate.now().plusDays(30))
                .custodianId(custodianId)
                .custodianName("保管员" + custodianId)
                .borrowStatus("IN_STOCK")
                .status("ACTIVE")
                .build();
    }
}
