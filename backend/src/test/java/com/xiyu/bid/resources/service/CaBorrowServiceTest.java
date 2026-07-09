package com.xiyu.bid.resources.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.resources.dto.CaBorrowApplicationDTO;
import com.xiyu.bid.resources.dto.CaBorrowRequest;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity.BorrowStatus;
import com.xiyu.bid.resources.entity.CaBorrowEventEntity;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.entity.CaCertificateEntity.CaBorrowStatus;
import com.xiyu.bid.resources.notification.CaNotificationDispatcher;
import com.xiyu.bid.resources.repository.CaBorrowApplicationRepository;
import com.xiyu.bid.resources.repository.CaBorrowEventRepository;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import com.xiyu.bid.resources.dto.CaReturnRequest;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

    // ── CO-546: returnBorrow 保留到期通知触发（即时性优先） ──

    /**
     * CO-546: 登记归还时仍应触发 CaNotificationDispatcher.onExpiring/onExpired。
     *
     * <p>定时扫描（每天 09:00）通过 DAILY_DEDUP 策略实现每日去重；
     * returnBorrow 是低频事件，保留即时通知比避免重复更重要。
     * 管理员登记归还时立即收到到期提醒，避免错过处理窗口。</p>
     */
    @Test
    void returnCertificate_shouldTriggerExpiryNotificationForExpiringCert() {
        CaBorrowService service = newService();
        User user = user(50L, "admin", "管理员", "EMP999");
        // 即将到期（5天后）+ 已借出状态
        CaCertificateEntity cert = CaCertificateEntity.builder()
                .id(1L)
                .caType("ENTITY_CA")
                .sealType("OFFICIAL_SEAL")
                .expiryDate(LocalDate.now().plusDays(5))
                .custodianId(99L)
                .custodianName("保管员99")
                .borrowStatus(CaBorrowStatus.IN_STOCK.name())
                .status("ACTIVE")
                .build();
        CaBorrowApplicationEntity app = CaBorrowApplicationEntity.builder()
                .id(100L)
                .caCertificateId(1L)
                .applicantId(50L)
                .applicantName("管理员")
                .status(BorrowStatus.APPROVED.name())
                .approverId(99L)
                .approverName("保管员99")
                .borrowDurationType("SHORT_TERM")
                .build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(borrowRepository.findById(100L)).thenReturn(Optional.of(app));
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(cert));
        when(effectiveRoleResolver.resolveRoleCode(user)).thenReturn("/bidAdmin");

        service.returnCertificate(100L, userDetails("admin"), returnRequest());

        // 关键断言：即将到期 CA 归还时应触发 onExpiring
        verify(caNotificationDispatcher).onExpiring(eq(cert), eq(5L));
    }

    @Test
    void returnCertificate_shouldTriggerExpiredNotificationForExpiredCert() {
        CaBorrowService service = newService();
        User user = user(50L, "admin", "管理员", "EMP999");
        // 已过期（1天前）+ 已借出状态
        CaCertificateEntity cert = CaCertificateEntity.builder()
                .id(1L)
                .caType("ENTITY_CA")
                .sealType("OFFICIAL_SEAL")
                .expiryDate(LocalDate.now().minusDays(1))
                .custodianId(99L)
                .custodianName("保管员99")
                .borrowStatus(CaBorrowStatus.IN_STOCK.name())
                .status("EXPIRED")
                .build();
        CaBorrowApplicationEntity app = CaBorrowApplicationEntity.builder()
                .id(100L)
                .caCertificateId(1L)
                .applicantId(50L)
                .applicantName("管理员")
                .status(BorrowStatus.APPROVED.name())
                .approverId(99L)
                .approverName("保管员99")
                .borrowDurationType("SHORT_TERM")
                .build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(borrowRepository.findById(100L)).thenReturn(Optional.of(app));
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(cert));
        when(effectiveRoleResolver.resolveRoleCode(user)).thenReturn("/bidAdmin");

        service.returnCertificate(100L, userDetails("admin"), returnRequest());

        // 关键断言：已过期 CA 归还时应触发 onExpired
        verify(caNotificationDispatcher).onExpired(eq(cert));
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

    private CaReturnRequest returnRequest() {
        CaReturnRequest req = new CaReturnRequest();
        req.setActualReturnDate(LocalDate.now());
        req.setReturnNotes("正常归还");
        return req;
    }
}
