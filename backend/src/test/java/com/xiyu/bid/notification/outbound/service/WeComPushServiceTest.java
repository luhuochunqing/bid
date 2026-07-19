package com.xiyu.bid.notification.outbound.service;

import com.xiyu.bid.auth.OAuthStateService;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.application.WeComIntegrationAppService;
import com.xiyu.bid.integration.application.WeComSsoConfig;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeComPushService — 按工号委托 WecomMessageSender 发企微")
class WeComPushServiceTest {

    private static final String PLATFORM_BASE_URL = "https://xiyu.example.com";

    @Mock private UserRepository userRepository;
    @Mock private WecomMessageSender wecomMessageSender;
    @Mock private WeComIntegrationAppService integrationAppService;
    @Mock private OAuthStateService oAuthStateService;

    private WeComPushService service;

    private static NotificationCreatedEvent event() {
        return new NotificationCreatedEvent(100L, List.of(7L), "MENTION", "你被提到", "PROJECT", 42L);
    }

    private static User userWithEmployee(String employeeNumber) {
        return User.builder().id(7L).username("u").email("a@x.com").password("p")
            .fullName("User").role(User.Role.MANAGER).employeeNumber(employeeNumber).build();
    }

    private static WeComSsoConfig ssoConfig() {
        return new WeComSsoConfig("wx045d055c4e7bab5e", "1000322");
    }

    @BeforeEach
    void setUp() {
        service = new WeComPushService(
            userRepository, wecomMessageSender, integrationAppService, oAuthStateService,
            PLATFORM_BASE_URL);
    }

    @Test
    @DisplayName("用户不存在 -> skip，不调用发送器")
    void userNotFound_skipped() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        NotificationDeliveryResult result = service.pushForRecipient(event(), 7L);

        assertThat(result.successful()).isTrue();
        assertThat(result.skipped()).isTrue();
        assertThat(result.message()).contains("employee number");
        verify(wecomMessageSender, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("用户无工号 -> skip，不调用发送器")
    void noEmployeeNumber_skipped() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithEmployee("")));

        NotificationDeliveryResult result = service.pushForRecipient(event(), 7L);

        assertThat(result.successful()).isTrue();
        assertThat(result.skipped()).isTrue();
        assertThat(result.message()).contains("employee number");
        verify(wecomMessageSender, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("发送成功 -> sent，收件人为工号")
    void successfulSend_returnsSuccess() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithEmployee("E007")));
        when(integrationAppService.getSsoConfig()).thenReturn(Optional.empty());
        when(wecomMessageSender.send(eq("E007"), anyString()))
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
        when(integrationAppService.getSsoConfig()).thenReturn(Optional.empty());
        when(wecomMessageSender.send(anyString(), anyString()))
            .thenReturn(WecomSendResult.failure(500, "crm down"));

        NotificationDeliveryResult result = service.pushForRecipient(event(), 7L);

        assertThat(result.successful()).isFalse();
        assertThat(result.errcode()).isEqualTo(500);
        assertThat(result.message()).isEqualTo("crm down");
    }

    @Test
    @DisplayName("SSO 未启用时 -> content 含直接业务 URL，链接用 <a> 标签包裹")
    void send_passesFormattedContent() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithEmployee("E007")));
        when(integrationAppService.getSsoConfig()).thenReturn(Optional.empty());
        when(wecomMessageSender.send(anyString(), anyString()))
            .thenReturn(WecomSendResult.success(0, "ok"));

        service.pushForRecipient(event(), 7L);

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(wecomMessageSender).send(eq("E007"), content.capture());
        String body = content.getValue();
        assertThat(body).contains(PLATFORM_BASE_URL);
        assertThat(body).contains("<a href=\"" + PLATFORM_BASE_URL);
        assertThat(body).contains("查看详情</a>");
    }

    @Test
    @DisplayName("发送器抛异常 -> 向上抛出，交由投递管线处理")
    void senderThrows_bubblesException() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithEmployee("E007")));
        when(integrationAppService.getSsoConfig()).thenReturn(Optional.empty());
        when(wecomMessageSender.send(anyString(), anyString()))
            .thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> service.pushForRecipient(event(), 7L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("timeout");
    }

    // ============ SSO 启用场景：消息 URL 构造为 OAuth 授权链接 ============

    @Test
    @DisplayName("SSO 启用时 -> 消息 URL 为 OAuth 授权链接，含 appid/agentid/state=msg:<uuid>")
    void ssoEnabled_messageUrlIsOAuthAuthorizeUrl() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithEmployee("E007")));
        when(integrationAppService.getSsoConfig()).thenReturn(Optional.of(ssoConfig()));
        when(oAuthStateService.storeStateForMessage()).thenReturn("msg:abc123def456");
        when(wecomMessageSender.send(anyString(), anyString()))
            .thenReturn(WecomSendResult.success(0, "ok"));

        service.pushForRecipient(event(), 7L);

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(wecomMessageSender).send(eq("E007"), content.capture());
        String body = content.getValue();
        // 关键断言：URL 是 OAuth 授权链接，而非直接业务 URL
        assertThat(body).contains("https://open.weixin.qq.com/connect/oauth2/authorize");
        assertThat(body).contains("appid=wx045d055c4e7bab5e");
        assertThat(body).contains("agentid=1000322");
        assertThat(body).contains("scope=snsapi_base");
        // state 是 msg:<uuid> 格式（防 Session Fixation），不是固定值 "msg"
        assertThat(body).contains("state=msg:abc123def456");
        // redirect_uri 应编码原目标 path（/project/42 被双重编码）
        assertThat(body).contains("redirect_uri=");
        assertThat(body).contains("redirect%3D");
        // 不应包含直接业务 URL（已被包装为 OAuth URL）
        assertThat(body).doesNotContain("<a href=\"https://xiyu.example.com/project/42\">");
        // 关键：调用了 storeStateForMessage（生成一次性 state）
        verify(oAuthStateService).storeStateForMessage();
    }

    @Test
    @DisplayName("getSsoConfig 返回 empty（未配置 / SSO disabled / 配置不全）-> 消息 URL 为直接业务 URL（向后兼容）")
    void ssoNotAvailable_messageUrlIsDirectBusinessUrl() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWithEmployee("E007")));
        when(integrationAppService.getSsoConfig()).thenReturn(Optional.empty());
        when(wecomMessageSender.send(anyString(), anyString()))
            .thenReturn(WecomSendResult.success(0, "ok"));

        service.pushForRecipient(event(), 7L);

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(wecomMessageSender).send(eq("E007"), content.capture());
        String body = content.getValue();
        // 关键断言：URL 是直接业务 URL，不含 OAuth
        assertThat(body).contains("<a href=\"https://xiyu.example.com/project/42\">");
        assertThat(body).doesNotContain("open.weixin.qq.com");
        // SSO 未启用时不应生成 state
        verify(oAuthStateService, never()).storeStateForMessage();
    }
}
