// Input: WorkbenchOpportunityQueryService（mock）
// Output: GET /api/workbench/opportunity-predictions 契约覆盖
// Pos: backend/src/test/java/com/xiyu/bid/workbench/controller/ - 控制器契约测试
package com.xiyu.bid.workbench.controller;

import com.xiyu.bid.workbench.dto.WorkbenchOpportunityDTO;
import com.xiyu.bid.workbench.service.WorkbenchOpportunityQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkbenchOpportunityControllerTest {

    @Mock
    private WorkbenchOpportunityQueryService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new WorkbenchOpportunityController(service))
                // 固定 Accept: application/json，避免 standalone MockMvc 在存在 XML 转换器时回退到 XML
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    @Test
    void shouldReturnOpportunityPredictions() throws Exception {
        when(service.getOpportunityPredictions(any(LocalDate.class))).thenReturn(List.of(
                new WorkbenchOpportunityDTO(
                        1L, "智慧水务一期工程", "WON", "XX市水务局", "张明",
                        "CENTRAL_SOE", "INDUSTRIAL", "2021-08-15",
                        new BigDecimal("5"), "2026-08-14", "2026-09-14", true)
        ));

        mvc.perform(get("/api/workbench/opportunity-predictions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].projectId").value(1))
                .andExpect(jsonPath("$.data[0].projectName").value("智慧水务一期工程"))
                .andExpect(jsonPath("$.data[0].resultType").value("WON"))
                .andExpect(jsonPath("$.data[0].customer").value("XX市水务局"))
                .andExpect(jsonPath("$.data[0].projectLeaderName").value("张明"))
                .andExpect(jsonPath("$.data[0].customerType").value("CENTRAL_SOE"))
                .andExpect(jsonPath("$.data[0].projectType").value("INDUSTRIAL"))
                .andExpect(jsonPath("$.data[0].bidOpenTime").value("2021-08-15"))
                .andExpect(jsonPath("$.data[0].servicePeriodYears").value(5))
                .andExpect(jsonPath("$.data[0].servicePeriodEndDate").value("2026-08-14"))
                .andExpect(jsonPath("$.data[0].predictedBidDate").value("2026-09-14"))
                .andExpect(jsonPath("$.data[0].near").value(true));

        verify(service).getOpportunityPredictions(any(LocalDate.class));
    }

    @Test
    void shouldReturnEmptyListWhenNoOpportunities() throws Exception {
        when(service.getOpportunityPredictions(any(LocalDate.class))).thenReturn(List.of());

        mvc.perform(get("/api/workbench/opportunity-predictions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
