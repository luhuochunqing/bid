package com.xiyu.bid.security;

import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.casework.application.ProjectArchiveWorkflowService;
import com.xiyu.bid.casework.application.service.KnowledgeCaseQueryAppService;
import com.xiyu.bid.casework.dto.ProjectArchiveStatsResponse;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.brandauth.manufacturer.application.service.ListManufacturerAuthAppService;
import com.xiyu.bid.brandauth.manufacturer.application.dto.ManufacturerAuthorizationDTO;
import com.xiyu.bid.personnel.application.service.ListPersonnelAppService;
import com.xiyu.bid.personnel.application.dto.PersonnelDTO;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CO-362 regression gate：投标专员（bid-Team）应能读取知识库模块列表接口。
 *
 * <p>投标专员登录后 authority 集含 catalog 授予的 {@code project}、{@code brand-auth.view}、
 * {@code personnel.view} 权限点。原 controller 类级 {@code hasAnyRole('ADMIN','MANAGER')} 把它挡住返回 403。
 * 修复后：
 * <ul>
 *   <li>GET /api/archive、/api/cases 用 {@code hasAuthority('project')} 放行</li>
 *   <li>GET /api/knowledge/brand-auth 用 {@code hasAuthority('brand-auth.view')} 放行</li>
 *   <li>CO-403: GET /api/knowledge/personnel 改用 {@code hasAuthority('personnel.view')} 放行，
 *       持有该权限点的角色（bid-projectLeader/bid-TeamLeader/bidAdmin/bid-Team）均可只读访问</li>
 * </ul>
 *
 * <p>本测试以 {@code @WithMockUser(authorities=...)} 模拟投标专员已拥有的权限点，
 * 断言知识库列表接口返回 200；同时以无权限用户断言仍 403，以 MANAGER 断言回归不破。
 *
 * <p>注意：GET /api/cases 在生产中还受 SecurityConfig 路径级
 * {@code .requestMatchers("/api/cases/**").hasAnyRole("ADMIN","MANAGER")} 兜底；
 * 本测试用 {@code permitAll()} 的 TestSecurityConfig，只验证方法级 @PreAuthorize，
 * 路径级兜底需在 SecurityConfig 改动时一并回归（见本次 SecurityConfig L173 改动）。
 *
 * <p>MANAGER 回归说明：生产中 MANAGER 走 {@code RoleProfileCatalog} 映射到 ADMIN（"all" 权限），
 * 故其 authority 集含 {@code project/brand-auth.view/personnel.view} 等；测试中用
 * {@code @WithMockUser(authorities={"ROLE_MANAGER","xxx"})} 显式带 ROLE_MANAGER + 对应 catalog 权限点，
 * 既回归"角色白名单仍在"（ROLE_MANAGER 保留），也回归"catalog 权限点放行仍在"。
 */
@WebMvcTest(controllers = {
        com.xiyu.bid.casework.controller.ProjectArchiveController.class,
        com.xiyu.bid.casework.controller.KnowledgeCaseController.class,
        com.xiyu.bid.personnel.infrastructure.controller.PersonnelController.class,
        com.xiyu.bid.brandauth.manufacturer.infrastructure.ManufacturerAuthorizationController.class
}, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                ApiKeyAuthenticationFilter.class}
))
@Import(KnowledgeAccessSecurityTest.TestSecurityConfig.class)
class KnowledgeAccessSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    // --- ProjectArchiveController 协作者 ---
    @MockBean
    private ProjectArchiveWorkflowService workflowService;
    @MockBean
    private com.xiyu.bid.casework.application.ProjectArchiveDetailService detailService;
    @MockBean
    private com.xiyu.bid.casework.application.ProjectArchiveExportService archiveExportService;
    @MockBean
    private com.xiyu.bid.casework.application.StreamingZipPackager streamingZipPackager;
    @MockBean
    private com.xiyu.bid.casework.infrastructure.ArchiveFileRepository archiveFileRepository;

    // --- KnowledgeCaseController 协作者 ---
    @MockBean
    private KnowledgeCaseQueryAppService queryAppService;
    @MockBean
    private com.xiyu.bid.casework.application.service.KnowledgeCaseCommandAppService commandAppService;
    @MockBean
    private com.xiyu.bid.casework.application.service.KnowledgeCaseRecommendAppService recommendAppService;
    @MockBean
    private com.xiyu.bid.casework.application.service.CaseReferenceAppService caseReferenceAppService;
    @MockBean
    private com.xiyu.bid.casework.application.CasePrecipitationAppService precipitationAppService;
    @MockBean
    private com.xiyu.bid.casework.application.service.CaseExportAppService caseExportZipAppService;
    @MockBean
    private com.xiyu.bid.casework.application.CaseExportExcelAppService caseExportExcelAppService;
    @MockBean
    private com.xiyu.bid.service.ProjectAccessScopeService projectAccessScopeService;

    // --- PersonnelController 协作者 ---
    @MockBean
    private ListPersonnelAppService listService;
    @MockBean
    private com.xiyu.bid.personnel.application.service.CreatePersonnelAppService createService;
    @MockBean
    private com.xiyu.bid.personnel.application.service.UpdatePersonnelAppService updateService;
    @MockBean
    private com.xiyu.bid.personnel.application.service.DeletePersonnelAppService deleteService;
    @MockBean
    private com.xiyu.bid.personnel.application.service.RestorePersonnelAppService restoreService;
    @MockBean
    private com.xiyu.bid.personnel.application.service.PersonnelOperationLogService operationLogService;
    @MockBean
    private com.xiyu.bid.personnel.domain.port.PersonnelFileStorage fileStorage;
    @MockBean
    private com.xiyu.bid.personnel.infrastructure.persistence.repository.PersonnelCertificateJpaRepository certJpaRepository;

    // --- ManufacturerAuthorizationController 协作者 ---
    @MockBean
    private ListManufacturerAuthAppService listManufacturerAuthAppService;
    @MockBean
    private com.xiyu.bid.brandauth.manufacturer.application.service.CreateManufacturerAuthAppService createManufacturerAuthAppService;
    @MockBean
    private com.xiyu.bid.brandauth.manufacturer.application.service.UpdateManufacturerAuthAppService updateManufacturerAuthAppService;
    @MockBean
    private com.xiyu.bid.brandauth.manufacturer.application.service.RevokeManufacturerAuthAppService revokeManufacturerAuthAppService;
    @MockBean
    private com.xiyu.bid.brandauth.manufacturer.application.service.AttachmentUploadAppService attachmentUploadAppService;
    // BrandAuthExportService 是 final 类，项目 Mockito 走 mock-maker-subclass 无法 @MockBean，
    // 且本测试所有端点（list/detail/revoke）都不调用它。改由 TestConfig 里 new 出真实实例注入。
    @MockBean
    private com.xiyu.bid.brandauth.manufacturer.domain.port.ManufacturerAuthorizationRepository manufacturerAuthorizationRepository;
    @MockBean
    private com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthAttachmentJpaRepository brandAuthAttachmentJpaRepository;
    @MockBean
    private com.xiyu.bid.brandauth.manufacturer.application.service.BrandAuthImportService brandAuthImportService;
    @MockBean
    private com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthOperationLogJpaRepository logRepository;

    @MockBean
    private UserRepository userRepository;

    // CO-373 回归修复：CurrentUserResolver 现依赖 EffectiveRoleResolver→RoleCodeCachePort，
    // @WebMvcTest 切片不实例化该链；TraceFilter(@Component) 又强依赖 CurrentUserResolver。
    // 此处 mock 整个 CurrentUserResolver 以满足 TraceFilter 注入，避免上下文加载失败。
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

        // BrandAuthExportService 是 final 类，subclass mock maker 无法 mock；
        // 本测试不调用其方法，故直接 new 出真实实例（用 MockBean 注入的两个 repository）。
        @Bean
        com.xiyu.bid.brandauth.manufacturer.application.service.BrandAuthExportService brandAuthExportService(
                com.xiyu.bid.brandauth.manufacturer.domain.port.ManufacturerAuthorizationRepository repository,
                com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthAttachmentJpaRepository attachmentRepository) {
            return new com.xiyu.bid.brandauth.manufacturer.application.service.BrandAuthExportService(
                    repository, attachmentRepository);
        }
    }

    // ==================== GET /api/archive ====================

    @Test
    @DisplayName("投标专员(authorities含project) GET /api/archive → 200")
    @WithMockUser(authorities = {"project"})
    void listArchives_shouldSucceed_forBidSpecialist() throws Exception {
        when(workflowService.queryProjectArchives(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        mockMvc.perform(get("/api/archive"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("无权限用户 GET /api/archive → 403")
    @WithMockUser(authorities = {})
    void listArchives_shouldReturn403_forNoPermission() throws Exception {
        mockMvc.perform(get("/api/archive"))
                .andExpect(status().isForbidden());
    }

    // ==================== GET /api/archive/{id} (CO-452 regression) ====================

    @Test
    @DisplayName("CO-452: 投标专员(authorities含project) GET /api/archive/{id} → 200")
    @WithMockUser(authorities = {"project"})
    void getArchiveDetail_shouldSucceed_forBidSpecialist() throws Exception {
        // Mock workflowService.findArchiveById 返回一个测试档案对象
        ProjectArchive mockArchive = new ProjectArchive();
        mockArchive.setId(1L);
        mockArchive.setProjectId(100L);
        when(workflowService.findArchiveById(1L)).thenReturn(mockArchive);

        // Mock detailService.getArchiveDetail 返回详情响应（完整参数）
        when(detailService.getArchiveDetail(1L))
                .thenReturn(new com.xiyu.bid.casework.dto.ProjectArchiveDetailResponse(
                        1L, 100L, "测试档案", null, null, null, null, null, null, null, null, null, null, List.of(), List.of()));

        mockMvc.perform(get("/api/archive/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CO-452回归验证：MANAGER(GET含project) GET /api/archive/{id} → 200")
    @WithMockUser(authorities = {"ROLE_MANAGER", "project"})
    void getArchiveDetail_shouldSucceed_forManager() throws Exception {
        // Mock workflowService.findArchiveById 返回一个测试档案对象
        ProjectArchive mockArchive = new ProjectArchive();
        mockArchive.setId(1L);
        mockArchive.setProjectId(100L);
        when(workflowService.findArchiveById(1L)).thenReturn(mockArchive);

        // Mock detailService.getArchiveDetail 返回详情响应（完整参数）
        when(detailService.getArchiveDetail(1L))
                .thenReturn(new com.xiyu.bid.casework.dto.ProjectArchiveDetailResponse(
                        1L, 100L, "测试档案", null, null, null, null, null, null, null, null, null, null, List.of(), List.of()));

        mockMvc.perform(get("/api/archive/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CO-452: 无project权限用户 GET /api/archive/{id} → 403")
    @WithMockUser(authorities = {"OTHER_PERMISSION"})
    void getArchiveDetail_shouldReturn403_forNoPermission() throws Exception {
        mockMvc.perform(get("/api/archive/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("回归：MANAGER(GET含project) GET /api/archive → 200")
    @WithMockUser(authorities = {"ROLE_MANAGER", "project"})
    void listArchives_shouldSucceed_forManager() throws Exception {
        when(workflowService.queryProjectArchives(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        mockMvc.perform(get("/api/archive"))
                .andExpect(status().isOk());
    }

    // ==================== GET /api/archive/stats ====================

    @Test
    @DisplayName("投标专员(authorities含project) GET /api/archive/stats → 200")
    @WithMockUser(authorities = {"project"})
    void archiveStats_shouldSucceed_forBidSpecialist() throws Exception {
        when(workflowService.getStats()).thenReturn(new ProjectArchiveStatsResponse(0L, 0L, 0L, 0L));
        mockMvc.perform(get("/api/archive/stats"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("无权限用户 GET /api/archive/stats → 403")
    @WithMockUser(authorities = {})
    void archiveStats_shouldReturn403_forNoPermission() throws Exception {
        mockMvc.perform(get("/api/archive/stats"))
                .andExpect(status().isForbidden());
    }

    // ==================== GET /api/cases ====================

    @Test
    @DisplayName("投标专员(authorities含project) GET /api/cases → 200")
    @WithMockUser(authorities = {"project"})
    void listCases_shouldSucceed_forBidSpecialist() throws Exception {
        when(queryAppService.queryCases(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        mockMvc.perform(get("/api/cases"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("无权限用户 GET /api/cases → 403")
    @WithMockUser(authorities = {})
    void listCases_shouldReturn403_forNoPermission() throws Exception {
        mockMvc.perform(get("/api/cases"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("回归：MANAGER(GET含project) GET /api/cases → 200")
    @WithMockUser(authorities = {"ROLE_MANAGER", "project"})
    void listCases_shouldSucceed_forManager() throws Exception {
        when(queryAppService.queryCases(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        mockMvc.perform(get("/api/cases"))
                .andExpect(status().isOk());
    }

    // ==================== GET /api/knowledge/personnel ====================

    @Test
    @DisplayName("投标专员(authorities含personnel.view) GET /api/knowledge/personnel → 200")
    @WithMockUser(authorities = {"personnel.view"})
    void listPersonnel_shouldSucceed_forBidSpecialist() throws Exception {
        when(listService.list(any())).thenReturn(List.<PersonnelDTO>of());
        mockMvc.perform(get("/api/knowledge/personnel"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("无权限用户 GET /api/knowledge/personnel → 403")
    @WithMockUser(authorities = {})
    void listPersonnel_shouldReturn403_forNoPermission() throws Exception {
        mockMvc.perform(get("/api/knowledge/personnel"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("回归：MANAGER(GET含personnel.view) GET /api/knowledge/personnel → 200")
    @WithMockUser(authorities = {"ROLE_MANAGER", "personnel.view"})
    void listPersonnel_shouldSucceed_forManager() throws Exception {
        when(listService.list(any())).thenReturn(List.<PersonnelDTO>of());
        mockMvc.perform(get("/api/knowledge/personnel"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CO-403: 投标项目负责人(持有personnel.view) GET /api/knowledge/personnel → 200")
    @WithMockUser(authorities = {"personnel.view"})
    void listPersonnel_shouldSucceed_forBidProjectLeader() throws Exception {
        when(listService.list(any())).thenReturn(List.<PersonnelDTO>of());
        mockMvc.perform(get("/api/knowledge/personnel"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CO-403: 投标项目负责人(持有personnel.view) GET /api/knowledge/personnel/{id} → 200")
    @WithMockUser(authorities = {"personnel.view"})
    void getPersonnel_shouldSucceed_forBidProjectLeader() throws Exception {
        when(listService.get(any())).thenReturn(null);
        mockMvc.perform(get("/api/knowledge/personnel/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CO-403: 仅持有 bid-projectLeader 角色（无 personnel.view 权限点） GET /api/knowledge/personnel → 403")
    @WithMockUser(authorities = {"bid-projectLeader"})
    void listPersonnel_shouldReturn403_forRoleWithoutPermissionPoint() throws Exception {
        mockMvc.perform(get("/api/knowledge/personnel"))
                .andExpect(status().isForbidden());
    }

    // ==================== GET /api/knowledge/brand-auth ====================

    @Test
    @DisplayName("投标专员(authorities含brand-auth.view) GET /api/knowledge/brand-auth → 200")
    @WithMockUser(authorities = {"brand-auth.view"})
    void listBrandAuth_shouldSucceed_forBidSpecialist() throws Exception {
        when(listManufacturerAuthAppService.list(any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<ManufacturerAuthorizationDTO>(List.of(), PageRequest.of(0, 20), 0));
        mockMvc.perform(get("/api/knowledge/brand-auth"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("无权限用户 GET /api/knowledge/brand-auth → 403")
    @WithMockUser(authorities = {})
    void listBrandAuth_shouldReturn403_forNoPermission() throws Exception {
        mockMvc.perform(get("/api/knowledge/brand-auth"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("回归：MANAGER(GET含brand-auth.view) GET /api/knowledge/brand-auth → 200")
    @WithMockUser(authorities = {"ROLE_MANAGER", "brand-auth.view"})
    void listBrandAuth_shouldSucceed_forManager() throws Exception {
        when(listManufacturerAuthAppService.list(any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<ManufacturerAuthorizationDTO>(List.of(), PageRequest.of(0, 20), 0));
        mockMvc.perform(get("/api/knowledge/brand-auth"))
                .andExpect(status().isOk());
    }

    // ==================== 写操作回归保护：投标专员不应越权敏感写操作 ====================

    @Test
    @DisplayName("CO-394: 投标专员(authorities含brand-auth.view,无brand-auth.revoke) POST /api/knowledge/brand-auth/{id}/revoke → 403")
    @WithMockUser(authorities = {"brand-auth.view"})
    void revokeBrandAuth_shouldReturn403_forBidSpecialist() throws Exception {
        // CO-394 后 revoke 方法级 @PreAuthorize("hasAuthority('brand-auth.revoke')")
        // 投标专员仅有 brand-auth.view 权限点，不含 brand-auth.revoke → 应返回 403。
        mockMvc.perform(post("/api/knowledge/brand-auth/1/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"测试作废原因至少十个字符\"}"))
                .andExpect(status().isForbidden());
    }
}
