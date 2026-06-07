package com.xiyu.bid.notification.outbound.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.application.WeComCredentialCipher;
import com.xiyu.bid.integration.application.WeComMessagePublisher;
import com.xiyu.bid.integration.domain.WeComSendResult;
import com.xiyu.bid.integration.infrastructure.persistence.entity.WeComIntegrationEntity;
import com.xiyu.bid.integration.infrastructure.persistence.repository.WeComIntegrationJpaRepository;
import com.xiyu.bid.notification.outbound.core.OutboundStatus;
import com.xiyu.bid.notification.outbound.core.SkipReason;
import com.xiyu.bid.notification.outbound.entity.OutboundLog;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.notification.outbound.repository.OutboundLogRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeComPushService — per-recipient push orchestration")
class WeComPushServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private WeComIntegrationJpaRepository integrationRepository;
    @Mock private WeComCredentialCipher cipher;
    @Mock private WeComMessagePublisher publisher;
    @Mock private OutboundLogRepository logRepository;

    private WeComPushService service;

    private static NotificationCreatedEvent event() {
        return new NotificationCreatedEvent(100L, List.of(7L), "MENTION", "你被提到", "PROJECT", 42L);
    }

    @BeforeEach
    void setUp() {
        service = new WeComPushService(
            userRepository, integrationRepository, cipher, publisher, logRepository,
            "https://xiyu.example.com");
    }

    @Test
    @DisplayName("no integration config → SKIPPED/DISABLED")
    void noIntegration_SkipsDisabled() {
        when(integrationRepository.findById(1L)).thenReturn(Optional.empty());

        service.pushForRecipient(event(), 7L);

        ArgumentCaptor<OutboundLog> captor = ArgumentCaptor.forClass(OutboundLog.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboundStatus.SKIPPED);
        assertThat(captor.getValue().getSkipReason()).isEqualTo(SkipReason.DISABLED);
    }

    @Test
    @DisplayName("message_enabled=false → SKIPPED/DISABLED")
    void disabled_SkipsDisabled() {
        WeComIntegrationEntity integration = new WeComIntegrationEntity();
        integration.setMessageEnabled(false);
        when(integrationRepository.findById(1L)).thenReturn(Optional.of(integration));

        service.pushForRecipient(event(), 7L);

        ArgumentCaptor<OutboundLog> captor = ArgumentCaptor.forClass(OutboundLog.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboundStatus.SKIPPED);
        assertThat(captor.getValue().getSkipReason()).isEqualTo(SkipReason.DISABLED);
    }

    @Test
    @DisplayName("user not bound → SKIPPED/NOT_BOUND")
    void notBound_SkipsNotBound() {
        WeComIntegrationEntity integration = new WeComIntegrationEntity();
        integration.setMessageEnabled(true);
        when(integrationRepository.findById(1L)).thenReturn(Optional.of(integration));

        User user = User.builder().id(7L).username("u").email("a@x.com").password("p")
            .fullName("User").role(User.Role.STAFF).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        service.pushForRecipient(event(), 7L);

        ArgumentCaptor<OutboundLog> captor = ArgumentCaptor.forClass(OutboundLog.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboundStatus.SKIPPED);
        assertThat(captor.getValue().getSkipReason()).isEqualTo(SkipReason.NOT_BOUND);
        verify(publisher, never()).sendTextMessage(anyString(), anyString(), anyString(), anyList(), anyString());
    }

    @Test
    @DisplayName("successful send → SENT")
    void successfulSend_WritesSent() {
        WeComIntegrationEntity integration = new WeComIntegrationEntity();
        integration.setCorpId("corp");
        integration.setAgentId("1000001");
        integration.setEncryptedSecret("enc");
        integration.setMessageEnabled(true);
        when(integrationRepository.findById(1L)).thenReturn(Optional.of(integration));

        User user = User.builder().id(7L).username("u").email("a@x.com").password("p")
            .fullName("User").role(User.Role.STAFF).wecomUserId("wc_007").build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        when(cipher.decrypt("enc")).thenReturn("plain-secret");
        when(publisher.sendTextMessage(eq("corp"), eq("1000001"), eq("plain-secret"),
            eq(List.of("wc_007")), anyString()))
            .thenReturn(new WeComSendResult(true, 0, "ok", List.of("wc_007")));

        service.pushForRecipient(event(), 7L);

        ArgumentCaptor<OutboundLog> captor = ArgumentCaptor.forClass(OutboundLog.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboundStatus.SENT);
        assertThat(captor.getValue().getSkipReason()).isNull();
    }

    @Test
    @DisplayName("WeCom returns failure → FAILED with errcode")
    void failedSend_WritesFailed() {
        WeComIntegrationEntity integration = new WeComIntegrationEntity();
        integration.setCorpId("corp");
        integration.setAgentId("1");
        integration.setEncryptedSecret("enc");
        integration.setMessageEnabled(true);
        when(integrationRepository.findById(1L)).thenReturn(Optional.of(integration));

        User user = User.builder().id(7L).username("u").email("a@x.com").password("p")
            .fullName("User").role(User.Role.STAFF).wecomUserId("wc_007").build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        when(cipher.decrypt("enc")).thenReturn("plain");
        when(publisher.sendTextMessage(anyString(), anyString(), anyString(), anyList(), anyString()))
            .thenReturn(new WeComSendResult(false, 40013, "invalid agentid", List.of("wc_007")));

        service.pushForRecipient(event(), 7L);

        ArgumentCaptor<OutboundLog> captor = ArgumentCaptor.forClass(OutboundLog.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboundStatus.FAILED);
        assertThat(captor.getValue().getWecomErrcode()).isEqualTo(40013);
        assertThat(captor.getValue().getWecomErrmsg()).isEqualTo("invalid agentid");
        assertThat(captor.getValue().getSkipReason()).isEqualTo(SkipReason.ERROR);
    }

    @Test
    @DisplayName("publisher throws → FAILED with truncated error message")
    void publisherThrows_WritesFailedWithTruncatedMessage() {
        WeComIntegrationEntity integration = new WeComIntegrationEntity();
        integration.setCorpId("corp");
        integration.setAgentId("1");
        integration.setEncryptedSecret("enc");
        integration.setMessageEnabled(true);
        when(integrationRepository.findById(1L)).thenReturn(Optional.of(integration));

        User user = User.builder().id(7L).username("u").email("a@x.com").password("p")
            .fullName("User").role(User.Role.STAFF).wecomUserId("wc_007").build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        when(cipher.decrypt("enc")).thenReturn("plain");
        String longMessage = "X".repeat(1000);
        when(publisher.sendTextMessage(anyString(), anyString(), anyString(), anyList(), anyString()))
            .thenThrow(new RuntimeException(longMessage));

        service.pushForRecipient(event(), 7L);

        ArgumentCaptor<OutboundLog> captor = ArgumentCaptor.forClass(OutboundLog.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboundStatus.FAILED);
        assertThat(captor.getValue().getWecomErrmsg()).hasSize(500);
    }
}
