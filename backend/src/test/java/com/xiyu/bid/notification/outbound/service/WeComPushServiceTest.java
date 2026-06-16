package com.xiyu.bid.notification.outbound.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.wecom.WecomMessageSender;
import com.xiyu.bid.wecom.WecomSendResult;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeComPushService — 按工号委托 WecomMessageSender 发企微")
class WeComPushServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private WecomMessageSender wecomMessageSender;

    private WeComPushService service;

    private static NotificationCreatedEvent event() {
        return new NotificationCreatedEvent(100L, List.of(7L), "MENTION", "你被提到", "PROJECT", 42L);
    }

    private static User userWithEmployee(String employeeNumber) {
        return User.builder().id(7L).username("u").email("a@x.com").password("p")
            .fullName("User").role(User.Role.STAFF).employeeNumber(employeeNumber).build();
    }

    @BeforeEach
    void setUp() {
        service = new WeComPushService(userRepository, wecomMessageSender, "https://xiyu.example.com");
    }

    @Test
    @DisplayName("用户不存在 -> skip，不调用发送器")
    void userNotFound_skipped() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        NotificationDeliveryResult result = service.pushForRecipient(event(), 7L);

        assertThat(result.successful()).isTrue();
        assertThat(result.skipped()).isTrue();
        assertThat(result.message()).contains("employee number");
        verify(wecomMessageSender, never()).send(anyList(), anyString(), anyString());
    }

    @Test
    @DisplayName("用户无工号 -> skip，不调用发送器")
    void noEmployeeNumber_skipped() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithEmployee("")));

        NotificationDeliveryResult result = service.pushForRecipient(event(), 7L);

        assertThat(result.successful()).isTrue();
        assertThat(result.skipped()).isTrue();
        assertThat(result.message()).contains("employee number");
        verify(wecomMessageSender, never()).send(anyList(), anyString(), anyString());
    }

    @Test
    @DisplayName("发送成功 -> sent，收件人为工号")
    void successfulSend_returnsSuccess() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithEmployee("E007")));
        when(wecomMessageSender.send(eq(List.of("E007")), anyString(), anyString()))
            .thenReturn(WecomSendResult.success(0, "ok"));

        NotificationDeliveryResult result = service.pushForRecipient(event(), 7L);

        assertThat(result.successful()).isTrue();
        assertThat(result.skipped()).isFalse();
        assertThat(result.errcode()).isEqualTo(0);
    }

    @Test
    @DisplayName("发送器返回 failure -> failed")
    void failedSend_returnsFailure() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithEmployee("E007")));
        when(wecomMessageSender.send(anyList(), anyString(), anyString()))
            .thenReturn(WecomSendResult.failure(500, "crm down"));

        NotificationDeliveryResult result = service.pushForRecipient(event(), 7L);

        assertThat(result.successful()).isFalse();
        assertThat(result.errcode()).isEqualTo(500);
        assertThat(result.message()).isEqualTo("crm down");
    }

    @Test
    @DisplayName("content 含格式化描述与深链 URL")
    void send_passesFormattedContent() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithEmployee("E007")));
        when(wecomMessageSender.send(anyList(), anyString(), anyString()))
            .thenReturn(WecomSendResult.success(0, "ok"));

        service.pushForRecipient(event(), 7L);

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(wecomMessageSender).send(eq(List.of("E007")), anyString(), content.capture());
        assertThat(content.getValue()).contains("https://xiyu.example.com");
    }

    @Test
    @DisplayName("发送器抛异常 -> 向上抛出，交由投递管线处理")
    void senderThrows_bubblesException() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithEmployee("E007")));
        when(wecomMessageSender.send(anyList(), anyString(), anyString()))
            .thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> service.pushForRecipient(event(), 7L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("timeout");
    }
}
