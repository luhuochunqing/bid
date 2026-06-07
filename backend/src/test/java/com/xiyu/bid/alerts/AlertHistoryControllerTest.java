package com.xiyu.bid.alerts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.alerts.controller.AlertHistoryController;
import com.xiyu.bid.alerts.dto.AlertHistoryResponse;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.service.AlertHistoryCommandService;
import com.xiyu.bid.alerts.service.AlertHistoryQueryService;
import com.xiyu.bid.alerts.service.AlertHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertHistoryController tests")
class AlertHistoryControllerTest {

    @Mock
    private AlertHistoryService alertHistoryService;

    @Mock
    private AlertHistoryQueryService alertHistoryQueryService;

    @Mock
    private AlertHistoryCommandService alertHistoryCommandService;

    private MockMvc mockMvc;
    private AlertHistoryController controller;

    @BeforeEach
    void setUp() {
        controller = new AlertHistoryController(
                alertHistoryService,
                alertHistoryQueryService,
                alertHistoryCommandService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        new ObjectMapper().findAndRegisterModules();
    }

    @Test
    @DisplayName("GET /api/alerts/history should pass filters through to query service")
    void shouldListAlertHistoryWithFilters() throws Exception {
        AlertHistoryResponse item = AlertHistoryResponse.builder()
                .id(12L)
                .ruleId(9L)
                .ruleName("保证金预警")
                .severity("HIGH")
                .message("保证金将在 2 天后到期")
                .status("ACTIVE")
                .relatedId("Deposit:88")
                .createdAt(LocalDateTime.of(2026, 4, 21, 8, 0))
                .build();
        when(alertHistoryQueryService.getAllAlertHistories(
                eq(PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("createdAt").descending())),
                eq("ACTIVE"),
                eq(AlertHistory.AlertLevel.HIGH),
                eq(9L),
                eq("Deposit:88")
        )).thenReturn(new PageImpl<>(List.of(item)));

        var response = controller.getAllAlertHistories(0, 10, "createdAt", "desc", "ACTIVE",
                AlertHistory.AlertLevel.HIGH, 9L, "Deposit:88");

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        org.assertj.core.api.Assertions.assertThat(response.getBody()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(response.getBody().getData().getContent()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(response.getBody().getData().getContent().get(0).getStatus()).isEqualTo("ACTIVE");
        org.assertj.core.api.Assertions.assertThat(response.getBody().getData().getContent().get(0).getRuleId()).isEqualTo(9L);
        org.assertj.core.api.Assertions.assertThat(response.getBody().getData().getContent().get(0).getRelatedId()).isEqualTo("Deposit:88");
    }

    @Test
    @DisplayName("PATCH /api/alerts/history/{id}/acknowledge should return updated payload")
    void shouldAcknowledgeAlertHistory() throws Exception {
        AlertHistoryResponse response = AlertHistoryResponse.builder()
                .id(12L)
                .status("ACKNOWLEDGED")
                .acknowledgedAt(LocalDateTime.of(2026, 4, 21, 8, 30))
                .build();
        when(alertHistoryCommandService.acknowledgeAlertHistory(12L)).thenReturn(response);

        mockMvc.perform(patch("/api/alerts/history/{id}/acknowledge", 12L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(12))
                .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"));

        verify(alertHistoryCommandService).acknowledgeAlertHistory(12L);
    }
}
