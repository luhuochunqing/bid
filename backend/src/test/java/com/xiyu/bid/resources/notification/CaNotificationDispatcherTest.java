package com.xiyu.bid.resources.notification;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.DispatchResult;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CaNotificationDispatcher 单测。
 *
 * <p>覆盖 PR !2193 新增/变更的 CA 通知文案字段，以及关键防御性分支（空证书、空保管员）。
 */
@ExtendWith(MockitoExtension.class)
class CaNotificationDispatcherTest {

    @Mock
    private NotificationApplicationService notificationService;
    @Mock
    private UserRepository userRepository;

    private CaNotificationDispatcher newDispatcher() {
        return new CaNotificationDispatcher(notificationService, userRepository);
    }

    @Test
    @DisplayName("onBorrowApproved: cert 为 null 时文案使用兜底'无'/'未知'，不影响通知发送")
    void onBorrowApproved_certNull_usesFallbackLabels() {
        CaNotificationDispatcher dispatcher = newDispatcher();
        CaBorrowApplicationEntity app = CaBorrowApplicationEntity.builder()
                .id(1L)
                .applicantId(100L)
                .projectName("投标项目A")
                .caCertificateId(999L)
                .build();
        when(notificationService.createNotification(any(CreateNotificationRequest.class), eq(null)))
                .thenReturn(DispatchResult.validWithId(1L));

        dispatcher.onBorrowApproved(app, null);

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), eq(null));
        CreateNotificationRequest req = captor.getValue();
        assertThat(req.body()).contains("关联平台：无");
        assertThat(req.body()).contains("CA类型：未知");
        assertThat(req.body()).contains("投标项目A");
        assertThat(req.recipientUserIds()).containsExactly(100L);
        assertThat(req.sourceEntityId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("onBorrowApproved: cert 存在时文案展示真实关联平台和 CA 类型")
    void onBorrowApproved_certPresent_usesCertificateLabels() {
        CaNotificationDispatcher dispatcher = newDispatcher();
        CaCertificateEntity cert = CaCertificateEntity.builder()
                .id(10L)
                .holderName("张三公司")
                .caType("ENTITY_CA")
                .relatedPlatforms("招标网,公共资源")
                .build();
        CaBorrowApplicationEntity app = CaBorrowApplicationEntity.builder()
                .id(1L)
                .applicantId(100L)
                .projectName("投标项目B")
                .caCertificateId(10L)
                .build();
        when(notificationService.createNotification(any(CreateNotificationRequest.class), eq(null)))
                .thenReturn(DispatchResult.validWithId(1L));

        dispatcher.onBorrowApproved(app, cert);

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), eq(null));
        assertThat(captor.getValue().body())
                .contains("关联平台：招标网,公共资源")
                .contains("CA类型：实体CA");
    }

    @Test
    @DisplayName("onBorrowSubmitted: cert 为 null 时直接跳过，不调用通知服务")
    void onBorrowSubmitted_certNull_skips() {
        CaNotificationDispatcher dispatcher = newDispatcher();
        CaBorrowApplicationEntity app = CaBorrowApplicationEntity.builder()
                .id(1L)
                .applicantId(100L)
                .build();

        dispatcher.onBorrowSubmitted(null, app);

        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("onBorrowSubmitted: cert 存在且 custodianId 有效时发送带新字段的待审批通知")
    void onBorrowSubmitted_validCert_dispatchesWithNewFields() {
        CaNotificationDispatcher dispatcher = newDispatcher();
        CaCertificateEntity cert = CaCertificateEntity.builder()
                .id(10L)
                .holderName("张三公司")
                .caType("ENTITY_CA")
                .relatedPlatforms("招标网")
                .custodianId(200L)
                .build();
        CaBorrowApplicationEntity app = CaBorrowApplicationEntity.builder()
                .id(1L)
                .applicantId(100L)
                .applicantName("李四")
                .borrowDurationType("SHORT")
                .build();
        when(notificationService.createNotification(any(CreateNotificationRequest.class), eq(null)))
                .thenReturn(DispatchResult.validWithId(1L));

        dispatcher.onBorrowSubmitted(cert, app);

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), eq(null));
        CreateNotificationRequest req = captor.getValue();
        assertThat(req.recipientUserIds()).containsExactly(200L);
        assertThat(req.body())
                .contains("关联平台：招标网")
                .contains("CA类型：实体CA")
                .contains("申请人：李四");
    }

    @Test
    @DisplayName("onExpiring: 包含剩余天数、关联平台和 CA 类型字段，并通知保管员与投标管理员")
    void onExpiring_certValid_dispatchesWithDaysLeftAndNewFields() {
        CaNotificationDispatcher dispatcher = newDispatcher();
        CaCertificateEntity cert = CaCertificateEntity.builder()
                .id(10L)
                .holderName("李四")
                .caType("ELECTRONIC_CA")
                .relatedPlatforms("A平台")
                .custodianId(200L)
                .expiryDate(java.time.LocalDate.now().plusDays(7))
                .build();
        User admin1 = User.builder().id(300L).build();
        when(userRepository.findEnabledByRoleProfileCodes(any(Set.class)))
                .thenReturn(List.of(admin1));
        when(notificationService.createNotification(any(CreateNotificationRequest.class), eq(null)))
                .thenReturn(DispatchResult.validWithId(1L));

        dispatcher.onExpiring(cert, 7L);

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), eq(null));
        CreateNotificationRequest req = captor.getValue();
        assertThat(req.recipientUserIds()).containsExactly(200L, 300L);
        assertThat(req.body())
                .contains("将在 7 天后到期")
                .contains("关联平台：A平台")
                .contains("CA类型：电子CA");
    }

    @Test
    @DisplayName("onExpired: 接收人包含 CA 保管员 + 投标管理员，且去重")
    void onExpired_recipientsCombineCustodianAndBidAdmins() {
        CaNotificationDispatcher dispatcher = newDispatcher();
        CaCertificateEntity cert = CaCertificateEntity.builder()
                .id(10L)
                .holderName("李四")
                .caType("ELECTRONIC_CA")
                .relatedPlatforms("A平台")
                .custodianId(200L)
                .expiryDate(java.time.LocalDate.now())
                .build();
        User admin1 = User.builder().id(300L).build();
        User admin2 = User.builder().id(301L).build();
        when(userRepository.findEnabledByRoleProfileCodes(any(Set.class)))
                .thenReturn(List.of(admin1, admin2));
        when(notificationService.createNotification(any(CreateNotificationRequest.class), eq(null)))
                .thenReturn(DispatchResult.validWithId(1L));

        dispatcher.onExpired(cert);

        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), eq(null));
        assertThat(captor.getValue().recipientUserIds()).containsExactly(200L, 300L, 301L);
    }
}
