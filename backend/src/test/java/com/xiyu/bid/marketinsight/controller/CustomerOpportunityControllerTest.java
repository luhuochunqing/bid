package com.xiyu.bid.marketinsight.controller;

import com.xiyu.bid.marketinsight.application.CustomerOpportunityAppService;
import com.xiyu.bid.marketinsight.dto.CustomerInsightDTO;
import com.xiyu.bid.marketinsight.dto.CustomerPredictionDTO;
import com.xiyu.bid.marketinsight.dto.request.CustomerInsightQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomerOpportunityControllerTest {

    @Mock
    private CustomerOpportunityAppService appService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerOpportunityController(appService)).build();
    }

    @Test
    void getCustomerInsights_shouldDelegateQueryToAppService() throws Exception {
        when(appService.getCustomerInsights(any(CustomerInsightQuery.class)))
                .thenReturn(List.of(CustomerInsightDTO.builder().customerId("C-1").customerName("测试客户").opportunityScore(88).status("recommend").build()));

        mockMvc.perform(get("/api/customer-opportunities/insights")
                        .param("status", "recommend")
                        .param("keyword", "测试")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].customerName").value("测试客户"))
                .andExpect(jsonPath("$.data[0].status").value("recommend"));

        ArgumentCaptor<CustomerInsightQuery> captor = ArgumentCaptor.forClass(CustomerInsightQuery.class);
        verify(appService).getCustomerInsights(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("recommend");
        assertThat(captor.getValue().getKeyword()).isEqualTo("测试");
    }

    @Test
    void convertPrediction_shouldReturnConvertedProjectId() throws Exception {
        when(appService.convertPrediction(9L, 801L)).thenReturn(CustomerPredictionDTO.builder()
                .opportunityId(9L)
                .customerId("HASH-9")
                .convertedProjectId(801L)
                .suggestedProjectName("华南采购项目")
                .build());

        mockMvc.perform(put("/api/customer-opportunities/predictions/{id}/convert", 9L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":801}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.convertedProjectId").value(801));
    }
}
