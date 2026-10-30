package com.xiyu.bid.wecom;

import com.xiyu.bid.wecom.infrastructure.WecomMessageCenterClient;
import com.xiyu.bid.wecom.infrastructure.WecomMessageCenterClient.MessageCenterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WecomMessageSender — 调用统一消息中心 /qywx/sendMSG")
class WecomMessageSenderTest {

    @Mock private WecomMessageCenterClient messageCenterClient;
    private WecomMessageSender sender;

    @BeforeEach
    void setUp() {
        sender = new WecomMessageSender(messageCenterClient);
    }

    @Test
    @DisplayName("消息中心 code=0 -> success")
    void messageCenterSuccess_returnsSuccess() {
        when(messageCenterClient.sendMessage(eq("E007"), eq("测试消息")))
            .thenReturn(new MessageCenterResponse(0, "ok", "trace-1"));

        WecomSendResult result = sender.send("E007", "测试消息");

        assertThat(result.success()).isTrue();
        assertThat(result.code()).isEqualTo(0);
        assertThat(result.message()).isEqualTo("ok");
    }

    @Test
    @DisplayName("消息中心 code!=0 -> failure")
    void messageCenterFailure_returnsFailure() {
        when(messageCenterClient.sendMessage(anyString(), anyString()))
            .thenReturn(new MessageCenterResponse(500, "busy", "trace-2"));

        WecomSendResult result = sender.send("E007", "测试消息");

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(500);
        assertThat(result.message()).isEqualTo("busy");
    }

    @Test
    void knownEmailIsPassedToMessageCenter() {
        when(messageCenterClient.sendMessage("E007", "测试消息", "employee@ehsy.com"))
                .thenReturn(new MessageCenterResponse(0, "ok", "trace-email"));
        assertThat(sender.send("E007", "测试消息", "employee@ehsy.com").success()).isTrue();
        verify(messageCenterClient).sendMessage("E007", "测试消息", "employee@ehsy.com");
    }

    @Test
    void blankEmailKeepsEmployeeNumberOnlyCall() {
        when(messageCenterClient.sendMessage("E007", "测试消息"))
                .thenReturn(new MessageCenterResponse(0, "ok", "trace-old"));
        assertThat(sender.send("E007", "测试消息", " ").success()).isTrue();
        verify(messageCenterClient).sendMessage("E007", "测试消息");
    }

    @Test
    @DisplayName("空 userName -> failure，不调用消息中心")
    void emptyUserName_returnsFailureWithoutCall() {
        WecomSendResult result = sender.send("", "测试消息");

        assertThat(result.success()).isFalse();
        verify(messageCenterClient, never()).sendMessage(anyString(), anyString());
    }

    @Test
    @DisplayName("空 message -> failure，不调用消息中心")
    void emptyMessage_returnsFailureWithoutCall() {
        WecomSendResult result = sender.send("E007", "  ");

        assertThat(result.success()).isFalse();
        verify(messageCenterClient, never()).sendMessage(anyString(), anyString());
    }
}
