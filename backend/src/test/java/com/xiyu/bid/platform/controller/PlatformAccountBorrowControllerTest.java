// Input: PlatformAccountBorrowService, UserRepository mocks
// Output: PlatformAccountBorrowController contract tests
// Pos: Test/控制器层 — 账号借用申请 API 契约验证

package com.xiyu.bid.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.GlobalExceptionHandler;
import com.xiyu.bid.platform.dto.BorrowApplicationDTO;
import com.xiyu.bid.platform.dto.BorrowApplicationRequest;
import com.xiyu.bid.platform.notification.PlatformAccountBorrowNotificationService;
import com.xiyu.bid.platform.service.PlatformAccountBorrowService;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformAccountBorrowControllerTest {

    private final PlatformAccountBorrowService borrowService = mock(PlatformAccountBorrowService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PlatformAccountBorrowNotificationService notificationService = mock(PlatformAccountBorrowNotificationService.class);
    // CO-403: 新增 EffectiveRoleResolver mock
    private final EffectiveRoleResolver effectiveRoleResolver = mock(EffectiveRoleResolver.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mockMvc;

    private static final User CURRENT_USER = User.builder().id(10L).username("sales").build();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PlatformAccountBorrowController(borrowService, userRepository, notificationService, effectiveRoleResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(userRepository.findByUsername("sales")).thenReturn(Optional.of(CURRENT_USER));
        // CO-403: 默认模拟非管理员角色
        when(effectiveRoleResolver.resolveRoleCode(any(User.class))).thenReturn("bid-Team");
    }

    @Test
    @DisplayName("提交借用申请成功")
    void submitApplication_success() throws Exception {
        BorrowApplicationDTO dto = sampleDto(100L, "PENDING_APPROVAL");
        when(borrowService.submitApplication(any(BorrowApplicationRequest.class), eq(CURRENT_USER)))
                .thenReturn(dto);

        mockMvc.perform(post("/api/platform/accounts/1/borrow-applications")
                        .principal(authToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BorrowApplicationRequest.builder()
                                .custodianId(20L)
                                .purpose("参与北京医院 IT 项目投标")
                                .projectId(5L)
                                .expectedReturnAt("2026-07-10T18:00:00")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("查询我的申请列表")
    void myApplications_success() throws Exception {
        when(borrowService.getApplications(eq(10L), isNull(), isNull()))
                .thenReturn(List.of(sampleDto(1L, "PENDING_APPROVAL")));

        mockMvc.perform(get("/api/borrow-applications/my-applications")
                        .principal(authToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("查询我的审批列表 — 普通用户按 custodianId 查询全部状态")
    void myApprovals_normalUser_success() throws Exception {
        when(borrowService.findAllApprovals(eq(10L)))
                .thenReturn(List.of(sampleDto(2L, "BORROWED"), sampleDto(3L, "RETURNED")));

        mockMvc.perform(get("/api/borrow-applications/my-approvals")
                        .principal(authToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(2));
    }

    @Test
    @DisplayName("CO-403: 查询我的审批列表 — 管理员查看全部申请（不限状态）")
    void myApprovals_privilegedUser_returnsAll() throws Exception {
        when(effectiveRoleResolver.resolveRoleCode(any(User.class))).thenReturn("/bidAdmin");
        when(borrowService.findAllApprovals(isNull()))
                .thenReturn(List.of(
                        sampleDto(1L, "PENDING_APPROVAL"),
                        sampleDto(2L, "BORROWED"),
                        sampleDto(3L, "RETURNED")
                ));

        mockMvc.perform(get("/api/borrow-applications/my-approvals")
                        .principal(authToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    @DisplayName("审批通过申请")
    void approveApplication_success() throws Exception {
        when(borrowService.approveApplication(eq(100L), eq("同意"), eq(CURRENT_USER), anyBoolean()))
                .thenReturn(sampleDto(100L, "BORROWED"));

        mockMvc.perform(post("/api/borrow-applications/100/approve")
                        .principal(authToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BORROWED"));
    }

    @Test
    @DisplayName("拒绝申请")
    void rejectApplication_success() throws Exception {
        when(borrowService.rejectApplication(eq(100L), eq("信息不完整"), eq(CURRENT_USER), anyBoolean()))
                .thenReturn(sampleDto(100L, "REJECTED"));

        mockMvc.perform(post("/api/borrow-applications/100/reject")
                        .principal(authToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"信息不完整\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    @DisplayName("撤销申请")
    void cancelApplication_success() throws Exception {
        when(borrowService.cancelApplication(eq(100L), eq(CURRENT_USER)))
                .thenReturn(sampleDto(100L, "CANCELLED"));

        mockMvc.perform(post("/api/borrow-applications/100/cancel")
                        .principal(authToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("归还账号并改密")
    void returnAccount_success() throws Exception {
        when(borrowService.returnAccount(eq(100L), eq("newSecret"), any(LocalDateTime.class), eq(CURRENT_USER), anyBoolean()))
                .thenReturn(sampleDto(100L, "RETURNED"));

        mockMvc.perform(post("/api/borrow-applications/100/return")
                        .principal(authToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newSecret\",\"actualReturnedAt\":\"2026-07-05T18:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"));
    }

    @Test
    @DisplayName("归还账号时实际归还时间格式非法返回业务错误")
    void returnAccount_invalidDateFormat_returnsBusinessError() throws Exception {
        mockMvc.perform(post("/api/borrow-applications/100/return")
                        .principal(authToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newSecret\",\"actualReturnedAt\":\"2026/07/05 18:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("实际归还时间格式不正确")));
    }

    private TestingAuthenticationToken authToken() {
        org.springframework.security.core.userdetails.UserDetails userDetails =
                org.springframework.security.core.userdetails.User
                        .withUsername("sales")
                        .password("ignored")
                        .authorities("ROLE_BID_PROJECTLEADER")
                        .build();
        TestingAuthenticationToken token = new TestingAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        token.setAuthenticated(true);
        return token;
    }

    private BorrowApplicationDTO sampleDto(Long id, String status) {
        return BorrowApplicationDTO.builder()
                .id(id)
                .accountId(1L)
                .applicantId(10L)
                .custodianId(20L)
                .purpose("投标使用")
                .status(status)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
