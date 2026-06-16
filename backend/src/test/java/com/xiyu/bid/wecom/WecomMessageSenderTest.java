package com.xiyu.bid.wecom;

import com.xiyu.bid.crm.application.CrmMessageService;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler.CrmApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WecomMessageSender — flag=3 调 CRM /common/sendMessage")
class WecomMessageSenderTest {

    @Mock private CrmMessageService crmMessageService;
    private WecomMessageSender sender;

    @BeforeEach
    void setUp() {
        sender = new WecomMessageSender(crmMessageService);
    }

    @Test
    @DisplayName("CRM 成功 -> success，透传 recipientNos 与 flag=3")
    void crmSuccess_returnsSuccess() {
        when(crmMessageService.sendMessage(eq(List.of("E001", "E002")), eq("标题"), eq("内容"), eq(3)))
            .thenReturn(new CrmApiResponse(0, "ok", null, true));

        WecomSendResult result = sender.send(List.of("E001", "E002"), "标题", "内容");

        assertThat(result.success()).isTrue();
        assertThat(result.code()).isEqualTo(0);
        assertThat(result.message()).isEqualTo("ok");
    }

    @Test
    @DisplayName("CRM 失败 -> failure")
    void crmFailure_returnsFailure() {
        when(crmMessageService.sendMessage(anyList(), anyString(), anyString(), eq(3)))
            .thenReturn(new CrmApiResponse(500, "boom", null, false));

        WecomSendResult result = sender.send(List.of("E001"), "标题", "内容");

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(500);
        assertThat(result.message()).isEqualTo("boom");
    }

    @Test
    @DisplayName("空 recipientNos -> failure，且不调用 CRM")
    void emptyRecipients_returnsFailureWithoutCall() {
        WecomSendResult result = sender.send(List.of(), "标题", "内容");

        assertThat(result.success()).isFalse();
        verify(crmMessageService, never()).sendMessage(anyList(), anyString(), anyString(), eq(3));
    }

    @Test
    @DisplayName("null recipientNos -> failure，且不调用 CRM")
    void nullRecipients_returnsFailureWithoutCall() {
        WecomSendResult result = sender.send(null, "标题", "内容");

        assertThat(result.success()).isFalse();
        verify(crmMessageService, never()).sendMessage(anyList(), anyString(), anyString(), eq(3));
    }
}
