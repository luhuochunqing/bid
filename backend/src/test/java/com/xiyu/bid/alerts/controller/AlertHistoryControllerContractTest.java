package com.xiyu.bid.alerts.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.alerts.dto.AlertHistoryResponse;
import com.xiyu.bid.alerts.dto.AlertStatisticsResponse;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.service.AlertHistoryCommandService;
import com.xiyu.bid.alerts.service.AlertHistoryQueryService;
import com.xiyu.bid.alerts.service.AlertHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
class AlertHistoryControllerContractTest {

    @Mock
    private AlertHistoryService alertHistoryService;

    @Mock
    private AlertHistoryQueryService alertHistoryQueryService;

    @Mock
    private AlertHistoryCommandService alertHistoryCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AlertHistoryController(alertHistoryService, alertHistoryQueryService, alertHistoryCommandService)
        ).build();
        new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void getAllAlertHistories_ShouldForwardFiltersAndReturnDtoPage() throws Exception {
        AlertHistoryResponse response = AlertHistoryResponse.builder()
                .id(12L)
                .status("ACKNOWLEDGED")
                .severity("HIGH")
                .message("保证金即将到期")
                .acknowledgedAt(LocalDateTime.of(2026, 4, 21, 10, 0))
                .build();

        when(alertHistoryQueryService.getAllAlertHistories(
                eq(PageRequest.of(0, 10, Sort.by("createdAt").descending())),
                eq("ACKNOWLEDGED"),
                eq(AlertHistory.AlertLevel.HIGH),
                eq(11L),
                eq("P-1")
        )).thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/alerts/history")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "createdAt")
                        .param("sortDir", "desc")
                        .param("status", "ACKNOWLEDGED")
                        .param("level", "HIGH")
                        .param("ruleId", "11")
                        .param("relatedId", "P-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(12))
                .andExpect(jsonPath("$.data.content[0].status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.data.content[0].message").value("保证金即将到期"));
    }

    @Test
    void acknowledgeAlertHistory_ShouldReturnDtoPayload() throws Exception {
        AlertHistoryResponse response = AlertHistoryResponse.builder()
                .id(8L)
                .status("ACKNOWLEDGED")
                .acknowledgedAt(LocalDateTime.of(2026, 4, 21, 12, 0))
                .build();
        when(alertHistoryCommandService.acknowledgeAlertHistory(8L)).thenReturn(response);

        mockMvc.perform(patch("/api/alerts/history/8/acknowledge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(8))
                .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"));

        verify(alertHistoryCommandService).acknowledgeAlertHistory(8L);
    }

    @Test
    void getAlertStatistics_ShouldReturnContractPayload() throws Exception {
        when(alertHistoryQueryService.getAlertStatistics()).thenReturn(AlertStatisticsResponse.builder()
                .totalAlerts(10L)
                .unresolvedAlerts(3L)
                .criticalAlerts(1L)
                .highAlerts(2L)
                .mediumAlerts(4L)
                .lowAlerts(3L)
                .build());

        mockMvc.perform(get("/api/alerts/history/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalAlerts").value(10))
                .andExpect(jsonPath("$.data.unresolvedAlerts").value(3));
    }
}
