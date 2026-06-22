package com.xiyu.bid.crm.infrastructure;

import com.xiyu.bid.crm.application.CrmChancePageResult;
import com.xiyu.bid.crm.application.CrmChanceService;
import com.xiyu.bid.crm.application.CrmContactPersonService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChancePageRequest;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceSearchByTenderRequest;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CRM 商机控制器集成/契约测试。
 * <p>验证 {@code POST /api/xiyu/crm/chances/search-by-tender} 端点的
 * 请求契约、响应契约以及权限规则。真实 CRM 调用由应用服务隔离，此处仅验证
 * 前后端与 Service 层之间的契约边界。
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrmChanceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CrmChanceService chanceService;

    @MockBean
    private CrmContactPersonService contactPersonService;

    @Test
    void searchByTender_shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/api/xiyu/crm/chances/search-by-tender")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenderer":"山东海化集团有限公司","registrationDeadline":"2026-06-03 23:59:00","bidOpeningTime":"2026-06-04 23:59:00","pageIndex":1,"pageSize":10}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "viewer", roles = {"USER"})
    void searchByTender_shouldRejectUnsupportedRole() throws Exception {
        mockMvc.perform(post("/api/xiyu/crm/chances/search-by-tender")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenderer":"山东海化集团有限公司","registrationDeadline":"2026-06-03 23:59:00","bidOpeningTime":"2026-06-04 23:59:00","pageIndex":1,"pageSize":10}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "staff", roles = {"STAFF"})
    void pageList_shouldReturnEmptyResultWhenServiceReturnsEmptyPage() throws Exception {
        CrmChancePageResult result =
                new CrmChancePageResult(List.of(), 0, 0, 0);
        when(chanceService.pageList(any(CustomerChancePageRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/xiyu/crm/chances/page-list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pageIndex":1,"pageSize":10,"body":{"selectAll":true}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isEmpty())
                .andExpect(jsonPath("$.data.totalCount").value(0));

        verify(chanceService).pageList(any(CustomerChancePageRequest.class));
    }

    @Test
    @WithMockUser(username = "staff", roles = {"STAFF"})
    void searchByTender_shouldAcceptBlueprintCriteriaAndReturnResult() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CustomerChanceVO chance = mapper.readValue("""
                {"id":1,"code":"CC-001","name":"海化集团MRO商机","groupName":"山东海化集团有限公司"}
                """, CustomerChanceVO.class);
        CrmChancePageResult result =
                new CrmChancePageResult(List.of(chance), 1, 10, 1);
        when(chanceService.searchByTender(any(CustomerChanceSearchByTenderRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/xiyu/crm/chances/search-by-tender")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenderer":"山东海化集团有限公司","registrationDeadline":"2026-06-03 23:59:00","bidOpeningTime":"2026-06-04 23:59:00","pageIndex":1,"pageSize":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.list[0].id").value(1))
                .andExpect(jsonPath("$.data.list[0].code").value("CC-001"))
                .andExpect(jsonPath("$.data.list[0].name").value("海化集团MRO商机"))
                .andExpect(jsonPath("$.data.totalCount").value(1));

        verify(chanceService).searchByTender(any(CustomerChanceSearchByTenderRequest.class));
    }
}
