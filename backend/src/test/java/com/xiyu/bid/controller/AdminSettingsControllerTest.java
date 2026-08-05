package com.xiyu.bid.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.admin.controller.AdminSettingsController;
import com.xiyu.bid.dto.DataScopeConfigResponse;
import com.xiyu.bid.admin.service.DataScopeConfigService;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminSettingsControllerTest {

    @Mock
    private DataScopeConfigService dataScopeConfigService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminSettingsController(dataScopeConfigService))
                .setControllerAdvice(new com.xiyu.bid.exception.GlobalExceptionHandler())
                // 固定 Accept: application/json，避免 standalone MockMvc 在存在 XML 转换器时回退到 XML
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void getDataScopeConfig_ShouldReturnPayload() throws Exception {
        // CO-605: service 层不再返回 deptOptions/userOptions（性能优化，前端由 deptTree/users 派生）
        when(dataScopeConfigService.getConfig()).thenReturn(DataScopeConfigResponse.builder()
                .deptTree(List.of(DataScopeConfigResponse.DepartmentTreeItem.builder()
                        .deptCode("SALES").deptName("销售部").sortOrder(1).build()))
                .build());

        mockMvc.perform(get("/api/admin/settings/data-scope"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deptTree[0].deptCode").value("SALES"))
                // deptOptions/userOptions 仍可能因 @Builder.Default 序列化为空数组，但不包含实际数据
                .andExpect(jsonPath("$.data.deptOptions").isEmpty())
                .andExpect(jsonPath("$.data.userOptions").isEmpty());
    }

    @Test
    void saveDataScopeConfig_ShouldReturnSavedPayload() throws Exception {
        DataScopeConfigResponse request = DataScopeConfigResponse.builder()
                .deptDataScope(List.of(DataScopeConfigResponse.DepartmentDataScopeItem.builder()
                        .deptCode("SALES")
                        .dataScope("dept")
                        .build()))
                .build();

        when(dataScopeConfigService.saveConfig(any(DataScopeConfigResponse.class))).thenReturn(request);

        mockMvc.perform(put("/api/admin/settings/data-scope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deptDataScope[0].deptCode").value("SALES"));
    }

    @Test
    void saveDepartmentTree_ShouldReturnNormalizedPayload() throws Exception {
        DataScopeConfigResponse response = DataScopeConfigResponse.builder()
                .deptTree(List.of(DataScopeConfigResponse.DepartmentTreeItem.builder()
                        .deptCode("SALES")
                        .deptName("销售部")
                        .sortOrder(1)
                        .build()))
                .build();
        when(dataScopeConfigService.saveDepartments(any())).thenReturn(response);

        mockMvc.perform(put("/api/admin/settings/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deptTree":[{"deptCode":"SALES","deptName":"销售部","sortOrder":1}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deptTree[0].deptCode").value("SALES"));
    }
}
