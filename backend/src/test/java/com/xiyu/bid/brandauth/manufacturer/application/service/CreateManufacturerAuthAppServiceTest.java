package com.xiyu.bid.brandauth.manufacturer.application.service;

import com.xiyu.bid.brandauth.manufacturer.application.command.CreateManufacturerAuthCommand;
import com.xiyu.bid.brandauth.manufacturer.application.dto.ManufacturerAuthorizationDTO;
import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.port.ManufacturerAuthorizationRepository;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthAttachmentEntity;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthAttachmentJpaRepository;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthOperationLogJpaRepository;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.BusinessException;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateManufacturerAuthAppServiceTest {

    @Mock
    private ManufacturerAuthorizationRepository repository;
    @Mock
    private BrandAuthAttachmentJpaRepository attachmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BrandAuthOperationLogJpaRepository logRepository;

    @InjectMocks
    private CreateManufacturerAuthAppService service;

    @BeforeEach
    void setUp() {
        ManufacturerAuthorization saved = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-001", "品牌",
                "国产", "原厂",
                LocalDate.now(), LocalDate.now().plusDays(180), null, 1L);
        // Use reflection to set id since record is immutable
        lenient().when(repository.save(any())).thenAnswer(inv -> {
            ManufacturerAuthorization a = inv.getArgument(0);
            return new ManufacturerAuthorization(42L, a.authorizationType(),
                    a.productLine(), a.brandId(), a.brandName(),
                    a.importDomestic(), a.manufacturerName(), a.agentName(),
                    a.authStartDate(), a.authEndDate(),
                    a.auth1StartDate(), a.auth1EndDate(), a.auth1Remarks(),
                    a.auth2StartDate(), a.auth2EndDate(), a.auth2Remarks(),
                    a.remarks(), a.status(), a.revokeReason(),
                    a.createdBy(), a.createdAt(), a.updatedAt(), a.version());
        });
        lenient().when(attachmentRepository.findByAuthorizationId(any()))
                .thenReturn(List.of());
        lenient().when(userRepository.findById(any()))
                .thenReturn(Optional.of(newUser(1L, "admin")));
    }

    @Test
    void createManufacturer_shouldSucceed_whenValidInput() {
        var cmd = new CreateManufacturerAuthCommand(
                "MANUFACTURER", ProductLine.TOOLS, "BR-001", "品牌",
                "国产", "原厂", null,
                LocalDate.now(), LocalDate.now().plusDays(180),
                null, null, null, null, null, null, null);

        var result = service.create(cmd, 1L);

        assertNotNull(result.dto());
        assertEquals("BR-001", result.dto().brandId());
        assertNull(result.warning());
    }

    @Test
    void createManufacturer_shouldThrow_whenEndDateNotAfterStart() {
        var cmd = new CreateManufacturerAuthCommand(
                "MANUFACTURER", ProductLine.TOOLS, "BR-001", "品牌",
                "国产", "原厂", null,
                LocalDate.now(), LocalDate.now(),
                null, null, null, null, null, null, null);

        assertThrows(BusinessException.class,
                () -> service.create(cmd, 1L));
    }

    @Test
    void createAgent_shouldSucceed_whenValidTimeChain() {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(365);
        LocalDate auth1End = end.minusDays(60);
        var cmd = new CreateManufacturerAuthCommand(
                "AGENT", ProductLine.CUTTING_TOOLS, "BR-002", "代理品牌",
                "进口", "原厂A", "代理商X",
                start, end,
                start, auth1End, "一级",
                start.plusDays(30), auth1End.minusDays(1), "二级",
                "备注");

        var result = service.create(cmd, 1L);

        assertNotNull(result.dto());
        assertEquals("AGENT", result.dto().authorizationType());
    }

    @Test
    void createAgent_shouldThrow_whenAgentNameBlank() {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(365);
        var cmd = new CreateManufacturerAuthCommand(
                "AGENT", ProductLine.TOOLS, "BR-003", "品牌",
                "进口", "原厂", "",
                start, end,
                start, end.minusDays(30), "一级",
                end.minusDays(29), end, "二级",
                "备注");

        assertThrows(BusinessException.class,
                () -> service.create(cmd, 1L));
    }

    @Test
    void createAgent_shouldThrow_whenAuth2EndAfterAuth1End() {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(365);
        var cmd = new CreateManufacturerAuthCommand(
                "AGENT", ProductLine.TOOLS, "BR-004", "品牌",
                "进口", "原厂", "代理商",
                start, end,
                start, end.minusDays(30), "一级",
                end.minusDays(29), end.plusDays(1), "二级",
                "备注");

        assertThrows(BusinessException.class,
                () -> service.create(cmd, 1L));
    }

    @Test
    void create_shouldLogOperation() {
        var cmd = new CreateManufacturerAuthCommand(
                "MANUFACTURER", ProductLine.TOOLS, "BR-005", "品牌",
                "国产", "原厂", null,
                LocalDate.now(), LocalDate.now().plusDays(180),
                null, null, null, null, null, null, null);

        service.create(cmd, 1L);

        ArgumentCaptor<com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthOperationLogEntity> captor =
                ArgumentCaptor.forClass(com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthOperationLogEntity.class);
        verify(logRepository).save(captor.capture());
        assertEquals("CREATE", captor.getValue().getActionType());
    }

    private static User newUser(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setFullName("管理员");
        return u;
    }
}
