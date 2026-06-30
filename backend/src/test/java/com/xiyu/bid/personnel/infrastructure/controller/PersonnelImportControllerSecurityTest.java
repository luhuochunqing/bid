package com.xiyu.bid.personnel.infrastructure.controller;

import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.personnel.application.service.ImportPersonnelAppService;
import com.xiyu.bid.personnel.application.service.ImportPersonnelAppService.ImportProgressInfo;
import com.xiyu.bid.personnel.domain.model.importtask.PersonnelImportTask;
import com.xiyu.bid.personnel.infrastructure.excel.PersonnelImportTemplateGenerator;
import com.xiyu.bid.security.CurrentUserResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CO-391 / CO-394-B 回归门禁：人员证书批量导入权限鉴权测试。
 *
 * <p>历史：CO-391 修复 bid_admin 06234 郑蓉蓉模板下载 403（漏写 'admin' 兜底 + roleCode 漂移）。
 * CO-394-B 将 PersonnelImportController 4 处方法级 {@code @PreAuthorize} 从
 * {@code hasAnyAuthority('admin','/bidAdmin','bid-TeamLeader','ROLE_BIDADMIN',...)}
 * 角色码白名单统一为 {@code hasAuthority('personnel.manage')} 权限点，对齐 Warehouse 模板。
 *
 * <p>测试以 {@code @WithMockUser(authorities=...)} 模拟不同 authority 组合，断言 4 个端点在
 * 持有 personnel.manage 权限点、仅持有旧角色码（无权限点）、无权限 3 种场景下的鉴权结果。
 */
@WebMvcTest(controllers = PersonnelImportController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                ApiKeyAuthenticationFilter.class}
))
@Import(PersonnelImportControllerSecurityTest.TestSecurityConfig.class)
class PersonnelImportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ImportPersonnelAppService importAppService;

    @MockBean
    private PersonnelImportTemplateGenerator templateGenerator;

    // CO-373 回归修复：CurrentUserResolver 依赖链在 @WebMvcTest 切片不实例化，
    // TraceFilter(@Component) 强依赖它，需 mock 以避免上下文加载失败。
    @MockBean
    private CurrentUserResolver currentUserResolver;

    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            // permitAll 让鉴权完全由 @PreAuthorize 决定，与生产方法安全语义一致
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    // ==================== GET /api/knowledge/personnel/import/template ====================

    @Test
    @DisplayName("持有 personnel.manage 权限点 GET /import/template → 200")
    @WithMockUser(authorities = {"personnel.manage"})
    void downloadTemplate_shouldSucceed_forPersonnelManagePermission() throws Exception {
        when(templateGenerator.generate()).thenReturn(new byte[]{0x50, 0x4B});
        mockMvc.perform(get("/api/knowledge/personnel/import/template"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("仅持有旧角色码(/bidAdmin)无 personnel.manage → 403（CO-394 后角色码不再直接鉴权）")
    @WithMockUser(authorities = {"/bidAdmin"})
    void downloadTemplate_shouldReturn403_forLegacyRoleCodeWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/knowledge/personnel/import/template"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("无权限用户 GET /import/template → 403")
    @WithMockUser(authorities = {})
    void downloadTemplate_shouldReturn403_forNoPermission() throws Exception {
        mockMvc.perform(get("/api/knowledge/personnel/import/template"))
                .andExpect(status().isForbidden());
    }

    // ==================== POST /api/knowledge/personnel/import ====================

    @Test
    @DisplayName("持有 personnel.manage 权限点 POST /import → 202")
    @WithMockUser(authorities = {"personnel.manage"})
    void startImport_shouldSucceed_forPersonnelManagePermission() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{0x50, 0x4B});
        PersonnelImportTask task = PersonnelImportTask.createNew("IMP-PER-TEST", 1L);
        when(importAppService.initiateImportTask(anyLong(), anyString())).thenReturn(task);
        mockMvc.perform(multipart("/api/knowledge/personnel/import").file(file))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("仅持有旧漂移角色码(ROLE_BIDADMIN)无 personnel.manage → 403（CO-394 后不再兜底）")
    @WithMockUser(authorities = {"ROLE_BIDADMIN"})
    void startImport_shouldReturn403_forDriftedRoleCodeWithoutPermission() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{0x50, 0x4B});
        mockMvc.perform(multipart("/api/knowledge/personnel/import").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("无权限用户 POST /import → 403")
    @WithMockUser(authorities = {})
    void startImport_shouldReturn403_forNoPermission() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{0x50, 0x4B});
        mockMvc.perform(multipart("/api/knowledge/personnel/import").file(file))
                .andExpect(status().isForbidden());
    }

    // ==================== GET /api/knowledge/personnel/import/{taskId} ====================

    @Test
    @DisplayName("持有 personnel.manage 权限点 GET /import/{taskId} → 200")
    @WithMockUser(authorities = {"personnel.manage"})
    void getImportProgress_shouldSucceed_forPersonnelManagePermission() throws Exception {
        when(importAppService.getProgress(anyLong())).thenReturn(
                new ImportProgressInfo("PROCESSING", 0, "处理中", 0, 0, 0));
        mockMvc.perform(get("/api/knowledge/personnel/import/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("无权限用户 GET /import/{taskId} → 403")
    @WithMockUser(authorities = {})
    void getImportProgress_shouldReturn403_forNoPermission() throws Exception {
        mockMvc.perform(get("/api/knowledge/personnel/import/1"))
                .andExpect(status().isForbidden());
    }

    // ==================== GET /api/knowledge/personnel/import/{taskId}/report ====================

    @Test
    @DisplayName("持有 personnel.manage 权限点 GET /import/{taskId}/report → 200")
    @WithMockUser(authorities = {"personnel.manage"})
    void downloadErrorReport_shouldSucceed_forPersonnelManagePermission() throws Exception {
        when(importAppService.getErrorReport(anyLong())).thenReturn(new byte[]{0x50, 0x4B});
        mockMvc.perform(get("/api/knowledge/personnel/import/1/report"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("无权限用户 GET /import/{taskId}/report → 403")
    @WithMockUser(authorities = {})
    void downloadErrorReport_shouldReturn403_forNoPermission() throws Exception {
        mockMvc.perform(get("/api/knowledge/personnel/import/1/report"))
                .andExpect(status().isForbidden());
    }
}
