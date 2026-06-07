package com.xiyu.bid.integration.organization.controller;

import com.xiyu.bid.integration.organization.application.OrganizationEventRetryAppService;
import com.xiyu.bid.integration.organization.application.OrganizationOperationsAppService;
import com.xiyu.bid.integration.organization.dto.OrganizationEventWebhookData;
import com.xiyu.bid.integration.organization.dto.OrganizationEventWebhookResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationOperationsController")
class OrganizationOperationsControllerTest {
    @Mock
    private OrganizationOperationsAppService operationsAppService;
    @Mock
    private OrganizationEventRetryAppService retryAppService;

    @Test
    @DisplayName("dead letter replay delegates to retry app service")
    void replayDeadLetter_delegates() {
        OrganizationEventWebhookResponse expected = new OrganizationEventWebhookResponse(
                "200",
                "success",
                1L,
                new OrganizationEventWebhookData("event-key", true, false, "PROCESSED")
        );
        when(retryAppService.replayDeadLetter(eq("event-key"), any(LocalDateTime.class)))
                .thenReturn(expected);
        OrganizationOperationsController controller = new OrganizationOperationsController(
                operationsAppService,
                retryAppService
        );

        OrganizationEventWebhookResponse response = controller.replayDeadLetter("event-key");

        ArgumentCaptor<LocalDateTime> replayAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(retryAppService).replayDeadLetter(eq("event-key"), replayAt.capture());
        assertThat(response).isSameAs(expected);
        assertThat(replayAt.getValue()).isNotNull();
    }
}
