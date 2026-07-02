package com.xiyu.bid.resources.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.resources.dto.CaBorrowApplicationDTO;
import com.xiyu.bid.resources.dto.CaBorrowRequest;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import com.xiyu.bid.resources.entity.CaBorrowEventEntity;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.entity.CaCertificateEntity.CaBorrowStatus;
import com.xiyu.bid.resources.notification.CaNotificationDispatcher;
import com.xiyu.bid.resources.repository.CaBorrowApplicationRepository;
import com.xiyu.bid.resources.repository.CaBorrowEventRepository;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-465: CA 借用申请"申请人"字段显示修复.
 *
 * <p>历史缺陷：CaBorrowService#borrow 把 {@code user.getUsername()}（登录账号）存进了
 * {@code applicant_name}，前端"我的审批"页只渲染裸字符串，导致列表显示登录账号而非
 * "姓名（工号）"。
 *
 * <p>修复契约：
 * <ul>
 *   <li>{@code applicant_name} 必须存 {@code user.getFullName()}（中文姓名）</li>
 *   <li>{@code applicant_employee_number} 必须存 {@code user.getDisplayEmployeeNumber()}</li>
 *   <li>事件流的 {@code actor_name} 同步存 fullName</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CaBorrowServiceTest {

    @Mock
    private CaCertificateRepository certificateRepository;
    @Mock
    private CaBorrowApplicationRepository borrowRepository;
    @Mock
    private CaBorrowEventRepository eventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CaNotificationDispatcher caNotificationDispatcher;
    @Mock
    private EffectiveRoleResolver effectiveRoleResolver;
    @Mock
    private CaBorrowApplicationNameEnricher nameEnricher;

    // ── borrow: 申请人字段必须存 fullName + employeeNumber ──

    @Test
    void borrow_applicantName_shouldStoreFullName_notUsername() {
        CaBorrowService service = newService();
        User user = user(10L, "xiaowang", "小王", "EMP001");
        CaCertificateEntity cert = inStockCert(1L, 99L);
        when(userRepository.findByUsername("xiaowang")).thenReturn(Optional.of(user));
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(cert));
        when(borrowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.borrow(userDetails("xiaowang"), borrowRequest(1L));

        ArgumentCaptor<CaBorrowApplicationEntity> captor =
                ArgumentCaptor.forClass(CaBorrowApplicationEntity.class);
        verify(borrowRepository).save(captor.capture());
        CaBorrowApplicationEntity saved = captor.getValue();
        assertThat(saved.getApplicantName()).isEqualTo("小王");
        assertThat(saved.getApplicantEmployeeNumber()).isEqualTo("EMP001");
    }

    @Test
    void borrow_applicantEmployeeNumber_shouldFallbackToUsername_whenEmployeeNumberBlank() {
        // org-synced 用户 employeeNumber 可能为空，应回退到 username（User#getDisplayEmployeeNumber 契约）
        CaBorrowService service = newService();
        User user = user(11L, "li.si", "李四", null);
        CaCertificateEntity cert = inStockCert(2L, 99L);
        when(userRepository.findByUsername("li.si")).thenReturn(Optional.of(user));
        when(certificateRepository.findById(2L)).thenReturn(Optional.of(cert));
        when(borrowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.borrow(userDetails("li.si"), borrowRequest(2L));

        ArgumentCaptor<CaBorrowApplicationEntity> captor =
                ArgumentCaptor.forClass(CaBorrowApplicationEntity.class);
        verify(borrowRepository).save(captor.capture());
        assertThat(captor.getValue().getApplicantEmployeeNumber()).isEqualTo("li.si");
    }

    @Test
    void borrow_eventActorName_shouldStoreFullName_notUsername() {
        CaBorrowService service = newService();
        User user = user(10L, "xiaowang", "小王", "EMP001");
        CaCertificateEntity cert = inStockCert(1L, 99L);
        when(userRepository.findByUsername("xiaowang")).thenReturn(Optional.of(user));
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(cert));
        when(borrowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.borrow(userDetails("xiaowang"), borrowRequest(1L));

        ArgumentCaptor<CaBorrowEventEntity> captor =
                ArgumentCaptor.forClass(CaBorrowEventEntity.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getActorName()).isEqualTo("小王");
    }

    @Test
    void borrow_dto_shouldExposeApplicantEmployeeNumber() {
        CaBorrowService service = newService();
        User user = user(10L, "xiaowang", "小王", "EMP001");
        CaCertificateEntity cert = inStockCert(1L, 99L);
        when(userRepository.findByUsername("xiaowang")).thenReturn(Optional.of(user));
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(cert));
        when(borrowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CaBorrowApplicationDTO dto = service.borrow(userDetails("xiaowang"), borrowRequest(1L));

        assertThat(dto.getApplicantName()).isEqualTo("小王");
        assertThat(dto.getApplicantEmployeeNumber()).isEqualTo("EMP001");
    }

    // ── helpers ──

    private CaBorrowService newService() {
        return new CaBorrowService(
                certificateRepository,
                borrowRepository,
                eventRepository,
                userRepository,
                caNotificationDispatcher,
                effectiveRoleResolver,
                nameEnricher
        );
    }

    private User user(Long id, String username, String fullName, String employeeNumber) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setFullName(fullName);
        u.setEmployeeNumber(employeeNumber);
        return u;
    }

    private CaCertificateEntity inStockCert(Long id, Long custodianId) {
        return CaCertificateEntity.builder()
                .id(id)
                .caType("ENTITY_CA")
                .sealType("OFFICIAL_SEAL")
                .expiryDate(LocalDate.now().plusDays(30))
                .custodianId(custodianId)
                .custodianName("保管员" + custodianId)
                .borrowStatus(CaBorrowStatus.IN_STOCK.name())
                .status("ACTIVE")
                .build();
    }

    private CaBorrowRequest borrowRequest(Long caCertificateId) {
        CaBorrowRequest req = new CaBorrowRequest();
        req.setCaCertificateId(caCertificateId);
        req.setPurpose("项目投标用章");
        req.setProjectId(1001L);
        req.setProjectName("测试项目");
        req.setBorrowDurationType("SHORT_TERM");
        req.setExpectedReturnDate(LocalDate.now().plusDays(7));
        return req;
    }

    private UserDetails userDetails(String username) {
        UserDetails ud = org.mockito.Mockito.mock(UserDetails.class);
        when(ud.getUsername()).thenReturn(username);
        return ud;
    }
}
