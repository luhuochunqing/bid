package com.xiyu.bid.notification.outbound.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.application.WeComCredentialCipher;
import com.xiyu.bid.integration.application.WeComMessagePublisher;
import com.xiyu.bid.integration.domain.WeComSendResult;
import com.xiyu.bid.integration.infrastructure.persistence.entity.WeComIntegrationEntity;
import com.xiyu.bid.integration.infrastructure.persistence.repository.WeComIntegrationJpaRepository;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeComPushService — returns delivery result")
class WeComPushServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private WeComIntegrationJpaRepository integrationRepository;
    @Mock private WeComCredentialCipher cipher;
    @Mock private WeComMessagePublisher publisher;

    private WeComPushService service;

    private static NotificationCreatedEvent event() {
        return new NotificationCreatedEvent(100L, List.of(7L), "MENTION", "你被提到", "PROJECT", 42L);
    }

    @BeforeEach
    void setUp() {
        service = new WeComPushService(
            userRepository, integrationRepository, cipher, publisher,
            "https://xiyu.example.com");
    }

    @Test
    @DisplayName("no integration config -> skipped result")
    void noIntegration_Skipped() {
        when(integrationRepository.findById(1L)).thenReturn(Optional.empty());

        NotificationDeliveryResult result = service.pushForRecipient(event(), 7L);

        assertThat(result.successful()).isTrue();
        assertThat(result.skipped()).isTrue();
        assertThat(result.message()).contains("disabled");
    }

    @Test
    @DisplayName("message_enabled=false -> skipped result")
    void disabled_Skipped() {
        WeComIntegrationEntity integration = new WeComIntegrationEntity();
        integration.setMessageEnabled(false);
        when(integrationRepository.findById(1L)).thenReturn(Optional.of(integration));

        NotificationDeliveryResult result = service.pushForRecipient(event(), 7L);

        assertThat(result.successful()).isTrue();
        assertThat(result.skipped()).isTrue();
        assertThat(result.message()).contains("disabled");
    }

    @Test
    @DisplayName("user not bound -> skipped result")
    void notBound_Skipped() {
        WeComIntegrationEntity integration = new WeComIntegrationEntity();
        integration.setMessageEnabled(true);
        when(integrationRepository.findById(1L)).thenReturn(Optional.of(integration));

        User user = User.builder().id(7L).username("u").email("a@x.com").password("p")
            .fullName("User").role(User.Role.STAFF).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        NotificationDeliveryResult result = service.pushForRecipient(event(), 7L);

        assertThat(result.successful()).isTrue();
        assertThat(result.skipped()).isTrue();
        assertThat(result.message()).contains("not bound");
        verify(publisher, never()).sendTextMessage(anyString(), anyString(), anyString(), anyList(), anyString());
    }

    @Test
    @DisplayName("successful send -> sent result")
    void successfulSend_ReturnsSuccess() {
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

        NotificationDeliveryResult result = service.pushForRecipient(event(), 7L);

        assertThat(result.successful()).isTrue();
        assertThat(result.skipped()).isFalse();
        assertThat(result.errcode()).isEqualTo(0);
    }

    @Test
    @DisplayName("WeCom returns failure -> failed result")
    void failedSend_ReturnsFailure() {
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

        NotificationDeliveryResult result = service.pushForRecipient(event(), 7L);

        assertThat(result.successful()).isFalse();
        assertThat(result.errcode()).isEqualTo(40013);
        assertThat(result.message()).isEqualTo("invalid agentid");
    }

    @Test
    @DisplayName("publisher throws -> bubble runtime exception")
    void publisherThrows_BubblesException() {
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
            .thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> service.pushForRecipient(event(), 7L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("timeout");
    }
}
