package com.xiyu.bid.tender.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.tender.dto.TenderImportProgressDTO;
import com.xiyu.bid.tender.dto.TenderImportTaskDTO;
import com.xiyu.bid.tender.service.TenderImportAppService;
import com.xiyu.bid.tender.service.TenderImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 标讯批量导入 Controller 契约测试（适配异步化后的 202 响应）。
 *
 * <p>覆盖契约：
 * <ul>
 *   <li>POST /api/tenders/import 返回 202 Accepted + TenderImportTaskDTO（含 taskId）</li>
 *   <li>POST /api/tenders/import 文件为空时返回 400</li>
 *   <li>GET /api/tenders/import/{taskId}/progress 返回 200 + TenderImportProgressDTO</li>
 * </ul>
 *
 * <p>注意：@Async 实际在新线程执行、MDC 传递、状态机终态等契约由
 * {@link com.xiyu.bid.tender.service.TenderImportAppServiceTest} 和
 * {@link com.xiyu.bid.tender.service.TenderImportProgressServiceTest} 等单测覆盖。
 */
class TenderImportControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private TenderImportAppService tenderImportAppService;
    private TenderImportService tenderImportService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        tenderImportAppService = mock(TenderImportAppService.class);
        tenderImportService = mock(TenderImportService.class);
        authService = mock(AuthService.class);

        // 使用反射构造 TenderController（绕开其他依赖的 mock）
        TenderController controller = org.mockito.Mockito.spy(
                new TenderController(
                        null, null, null, null,
                        tenderImportService, tenderImportAppService,
                        null, null, null, null, authService));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new com.xiyu.bid.exception.GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/tenders/import: 异步触发成功 → 202 Accepted + TenderImportTaskDTO")
    void importTenders_asyncTrigger_returns202() throws Exception {
        Long userId = 100L;
        when(authService.resolveUserIdByUsername("admin-user")).thenReturn(userId);

        TenderImportTaskDTO taskDTO = new TenderImportTaskDTO(
                "task-uuid-123", "PENDING", 0, 0, 0, 0, "导入任务已创建，请通过 taskId 查询进度");
        when(tenderImportAppService.triggerImport(any(MultipartFile.class), eq(userId)))
                .thenReturn(taskDTO);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3});

        authenticateAs("admin-user");

        mockMvc.perform(multipart("/api/tenders/import").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskId").value("task-uuid-123"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.msg").value("导入任务已创建，请通过 taskId 查询进度"));
    }

    @Test
    @DisplayName("POST /api/tenders/import: 文件为空 → 400 Bad Request")
    void importTenders_emptyFile_returns400() throws Exception {
        when(authService.resolveUserIdByUsername("admin-user")).thenReturn(100L);

        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]);

        authenticateAs("admin-user");

        mockMvc.perform(multipart("/api/tenders/import").file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/tenders/import/{taskId}/progress: 返回 200 + 进度 DTO")
    void getImportProgress_returns200() throws Exception {
        Long userId = 100L;
        String taskId = "task-uuid-123";
        when(authService.resolveUserIdByUsername("admin-user")).thenReturn(userId);

        TenderImportProgressDTO progressDTO = new TenderImportProgressDTO(
                taskId, "COMPLETED", 100, 100, 95, 5, 100,
                null, LocalDateTime.now().minusMinutes(5), LocalDateTime.now());
        when(tenderImportAppService.getProgress(taskId, userId)).thenReturn(progressDTO);

        authenticateAs("admin-user");

        mockMvc.perform(get("/api/tenders/import/" + taskId + "/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalRows").value(100))
                .andExpect(jsonPath("$.data.successCount").value(95))
                .andExpect(jsonPath("$.data.percent").value(100));
    }

    private void authenticateAs(String username) {
        org.springframework.security.core.userdetails.UserDetails principal =
                org.springframework.security.core.userdetails.User.withUsername(username)
                        .password("password")
                        .roles("ADMIN")
                        .build();
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }
}
