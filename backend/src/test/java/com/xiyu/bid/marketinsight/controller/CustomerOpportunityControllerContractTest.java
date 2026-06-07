package com.xiyu.bid.marketinsight.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.exception.GlobalExceptionHandler;
import com.xiyu.bid.marketinsight.application.CustomerOpportunityAppService;
import com.xiyu.bid.marketinsight.dto.CustomerInsightDTO;
import com.xiyu.bid.marketinsight.dto.CustomerPredictionDTO;
import com.xiyu.bid.marketinsight.dto.CustomerPurchaseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomerOpportunityControllerContractTest {

    @Mock
    private CustomerOpportunityAppService appService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerOpportunityController(appService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void getCustomerInsights_shouldPreserveRouteAndResponseEnvelope() throws Exception {
        when(appService.getCustomerInsights(any())).thenReturn(List.of(
                CustomerInsightDTO.builder()
                        .customerId("hash-1")
                        .customerName("国网江苏省电力")
                        .status("watch")
                        .build()));

        mockMvc.perform(get("/api/customer-opportunities/insights")
                        .param("status", "watch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].customerId").value("hash-1"))
                .andExpect(jsonPath("$.data[0].customerName").value("国网江苏省电力"));
    }

    @Test
    void getCustomerPurchases_shouldPreservePurchasesRouteAndEnvelope() throws Exception {
        when(appService.getCustomerPurchases("hash-1")).thenReturn(List.of(
                CustomerPurchaseDTO.builder()
                        .recordId(11L)
                        .customerId("hash-1")
                        .title("办公设备采购")
                        .build()));

        mockMvc.perform(get("/api/customer-opportunities/hash-1/purchases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].recordId").value(11))
                .andExpect(jsonPath("$.data[0].customerId").value("hash-1"));
    }

    @Test
    void refreshInsights_shouldPreserveCommandRoute() throws Exception {
        mockMvc.perform(post("/api/customer-opportunities/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void transitionPrediction_shouldPreserveStatusCommandRoute() throws Exception {
        when(appService.transitionPrediction(1L, "RECOMMEND")).thenReturn(
                CustomerPredictionDTO.builder()
                        .opportunityId(1L)
                        .customerId("hash-1")
                        .predictedCategory("能源电力")
                        .build());

        mockMvc.perform(put("/api/customer-opportunities/predictions/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "recommend"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.opportunityId").value(1))
                .andExpect(jsonPath("$.data.customerId").value("hash-1"));
    }
}
