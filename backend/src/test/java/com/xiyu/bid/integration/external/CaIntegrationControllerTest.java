// Input: CaIntegrationController（构造注入 CaCertificateService）
// Output: MockMvc 测试 — 验证对外 CA 查询接口的返参格式、密码脱敏和异常边界
// Pos: Test/合约验证
package com.xiyu.bid.integration.external;

import com.xiyu.bid.resources.dto.CaCertificateDTO;
import com.xiyu.bid.resources.service.CaBusinessException;
import com.xiyu.bid.resources.service.CaCertificateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link CaIntegrationController} 的 MockMvc 测试.
 *
 * <p>验证对外 CA 查询接口的契约：
 * <ul>
 *   <li>返参格式（success/code/msg/data + 分页字段）</li>
 *   <li>密码字段脱敏为 ******（对外接口禁止泄漏明文密码）</li>
 *   <li>三个端点路径正确</li>
 * </ul>
 *
 * <p>注意：standaloneSetup 模式不启用 Spring Security，
 * @PreAuthorize 注解不生效，安全性由 ApiKeyAuthenticationFilterTest 覆盖。
 */
@ExtendWith(MockitoExtension.class)
class CaIntegrationControllerTest {

    @Mock
    private CaCertificateService caCertificateService;

    @InjectMocks
    private CaIntegrationController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /api/integration/ca-certificates 返回 200 + 标准分页结构")
    void listCaCertificates_shouldReturnPagedResult() throws Exception {
        when(caCertificateService.list(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(dummyDTO()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/integration/ca-certificates")
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("列表查询密码字段脱敏为 ******")
    void listCaCertificates_passwordShouldBeMasked() throws Exception {
        when(caCertificateService.list(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(dummyDTO()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/integration/ca-certificates")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.content[0].caPassword").value("******"));
    }

    @Test
    @DisplayName("GET /api/integration/ca-certificates/overview 返回统计计数")
    void overview_shouldReturnCounts() throws Exception {
        when(caCertificateService.getOverview())
                .thenReturn(Map.of("total", 10L, "expiring", 2L, "expired", 1L, "borrowed", 3L));

        mockMvc.perform(get("/api/integration/ca-certificates/overview")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(10))
                .andExpect(jsonPath("$.data.expiring").value(2))
                .andExpect(jsonPath("$.data.expired").value(1))
                .andExpect(jsonPath("$.data.borrowed").value(3));
    }

    @Test
    @DisplayName("GET /api/integration/ca-certificates/{id} 返回单条 CA 详情")
    void getCaCertificate_shouldReturnDetail() throws Exception {
        CaCertificateDTO dto = dummyDTO();
        when(caCertificateService.getById(eq(1L))).thenReturn(dto);

        mockMvc.perform(get("/api/integration/ca-certificates/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.caType").value("ENTITY_CA"))
                .andExpect(jsonPath("$.data.holderName").value("测试持有人"));
    }

    @Test
    @DisplayName("详情查询密码字段脱敏为 ******")
    void getCaCertificate_passwordShouldBeMasked() throws Exception {
        CaCertificateDTO dto = dummyDTO();
        when(caCertificateService.getById(eq(1L))).thenReturn(dto);

        mockMvc.perform(get("/api/integration/ca-certificates/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.caPassword").value("******"));
    }

    /** 构造测试用 CA DTO，密码字段已脱敏（模拟 CaCertificateService 的默认行为）。 */
    private CaCertificateDTO dummyDTO() {
        return CaCertificateDTO.builder()
                .id(1L)
                .relatedPlatforms("平台A")
                .caType("ENTITY_CA")
                .sealType("OFFICIAL_SEAL")
                .electronicAccount("")
                .caPassword("******")
                .issuer("测试颁发机构")
                .holderName("测试持有人")
                .expiryDate(LocalDate.of(2027, 12, 31))
                .caPlatformUrl("https://example.com")
                .custodianId(100L)
                .custodianName("保管人")
                .custodianEmployeeNumber("EMP001")
                .borrowStatus("IN_STOCK")
                .status("ACTIVE")
                .remarks("测试备注")
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 2, 0, 0))
                .build();
    }

    // ========== 异常处理边界测试 ==========

    @Test
    @DisplayName("ID 不存在返回 404 + 标准错误格式（而非 500 兜底）")
    void getCaCertificate_notFound_shouldReturn404() throws Exception {
        when(caCertificateService.getById(eq(999L)))
                .thenThrow(new CaBusinessException("CA证书不存在: 999"));

        mockMvc.perform(get("/api/integration/ca-certificates/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("CA证书不存在: 999"));
    }

    @Test
    @DisplayName("分页 size > 100 被截断为 100（防过大拉取）")
    void listCaCertificates_sizeTooBig_shouldBeCapped() throws Exception {
        when(caCertificateService.list(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    // 验证传给 service 的 Pageable size 已被截断为 100
                    org.springframework.data.domain.Pageable passedPageable = invocation.getArgument(5);
                    assert passedPageable.getPageSize() == 100 : "size should be capped to 100";
                    return new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
                });

        mockMvc.perform(get("/api/integration/ca-certificates")
                        .param("size", "9999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    @DisplayName("分页 size <= 0 被 Spring resolver 转为默认值 20（Controller 层兜底是第二道防线）")
    void listCaCertificates_sizeZero_shouldUseDefaultSize() throws Exception {
        when(caCertificateService.list(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    // PageableHandlerMethodArgumentResolver 对 size<=0 用 @PageableDefault(size=20) 兜底
                    org.springframework.data.domain.Pageable passedPageable = invocation.getArgument(5);
                    assert passedPageable.getPageSize() == 20 : "Spring resolver 应把 size=0 兜底为 20，实际=" + passedPageable.getPageSize();
                    return new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
                });

        mockMvc.perform(get("/api/integration/ca-certificates")
                        .param("size", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    @DisplayName("分页 page < 0 被截断为 0（防非法负页码）")
    void listCaCertificates_negativePage_shouldBeZero() throws Exception {
        when(caCertificateService.list(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    org.springframework.data.domain.Pageable passedPageable = invocation.getArgument(5);
                    assert passedPageable.getPageNumber() == 0 : "page should be floored to 0";
                    return new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
                });

        mockMvc.perform(get("/api/integration/ca-certificates")
                        .param("page", "-5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0));
    }
}
