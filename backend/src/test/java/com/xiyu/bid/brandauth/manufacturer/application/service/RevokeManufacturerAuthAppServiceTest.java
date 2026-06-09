package com.xiyu.bid.brandauth.manufacturer.application.service;

import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.port.ManufacturerAuthorizationRepository;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthAttachmentJpaRepository;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthOperationLogJpaRepository;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RevokeManufacturerAuthAppServiceTest {

    @Mock
    private ManufacturerAuthorizationRepository repository;
    @Mock
    private BrandAuthAttachmentJpaRepository attachmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BrandAuthOperationLogJpaRepository logRepository;

    @InjectMocks
    private RevokeManufacturerAuthAppService service;

    @BeforeEach
    void setUp() {
        ManufacturerAuthorization active = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-001", "品牌",
                "国产", "原厂",
                LocalDate.now(), LocalDate.now().plusDays(180), null, 1L);
        active = new ManufacturerAuthorization(1L, active.authorizationType(),
                active.productLine(), active.brandId(), active.brandName(),
                active.importDomestic(), active.manufacturerName(), active.agentName(),
                active.authStartDate(), active.authEndDate(),
                active.auth1StartDate(), active.auth1EndDate(), active.auth1Remarks(),
                active.auth2StartDate(), active.auth2EndDate(), active.auth2Remarks(),
                active.remarks(), active.status(), active.revokeReason(),
                active.createdBy(), active.createdAt(), active.updatedAt(), active.version());

        lenient().when(repository.findById(1L)).thenReturn(Optional.of(active));
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(attachmentRepository.findByAuthorizationId(any()))
                .thenReturn(List.of());
        lenient().when(userRepository.findById(any()))
                .thenReturn(Optional.of(newUser(1L, "admin")));
    }

    @Test
    void revoke_shouldSucceed_whenValidReason() {
        var result = service.revoke(1L, "合同到期终止合作关系", 1L);

        assertEquals("已作废", result.status());
        assertEquals("合同到期终止合作关系", result.revokeReason());
    }

    @Test
    void revoke_shouldThrow_whenReasonTooShort() {
        assertThrows(IllegalArgumentException.class,
                () -> service.revoke(1L, "太短", 1L));
    }

    @Test
    void revoke_shouldThrow_whenRecordNotFound() {
        assertThrows(NoSuchElementException.class,
                () -> service.revoke(999L, "合同到期终止合作关系", 1L));
    }

    @Test
    void revoke_shouldThrow_whenAlreadyRevoked() {
        ManufacturerAuthorization revoked = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-001", "品牌",
                "国产", "原厂",
                LocalDate.now(), LocalDate.now().plusDays(180), null, 1L)
                .withRevokeReason("已作废");
        revoked = new ManufacturerAuthorization(2L, revoked.authorizationType(),
                revoked.productLine(), revoked.brandId(), revoked.brandName(),
                revoked.importDomestic(), revoked.manufacturerName(), revoked.agentName(),
                revoked.authStartDate(), revoked.authEndDate(),
                revoked.auth1StartDate(), revoked.auth1EndDate(), revoked.auth1Remarks(),
                revoked.auth2StartDate(), revoked.auth2EndDate(), revoked.auth2Remarks(),
                revoked.remarks(), revoked.status(), revoked.revokeReason(),
                revoked.createdBy(), revoked.createdAt(), revoked.updatedAt(), revoked.version());

        when(repository.findById(2L)).thenReturn(Optional.of(revoked));

        assertThrows(IllegalStateException.class,
                () -> service.revoke(2L, "再次作废原因说明文字加长", 1L));
    }

    @Test
    void revoke_shouldLogOperation() {
        service.revoke(1L, "合同到期终止合作关系", 1L);

        ArgumentCaptor<com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthOperationLogEntity> captor =
                ArgumentCaptor.forClass(com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthOperationLogEntity.class);
        verify(logRepository).save(captor.capture());
        assertEquals("REVOKE", captor.getValue().getActionType());
        assertEquals("作废授权", captor.getValue().getSummary());
    }

    private static User newUser(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setFullName("管理员");
        return u;
    }
}
