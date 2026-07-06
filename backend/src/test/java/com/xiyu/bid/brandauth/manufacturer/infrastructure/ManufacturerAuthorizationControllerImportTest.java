// Input: 模拟 multipart 文件上传请求
// Output: 验证 POST /api/knowledge/brand-auth/import 能正确解析 multipart
// Pos: backend test source - 单元级 MockMvc (standalone)
// 维护声明: 覆盖 XIYU-R 修复：AccessLogFilter 提前读取 input stream 导致 MultipartException。
package com.xiyu.bid.brandauth.manufacturer.infrastructure;

import com.xiyu.bid.brandauth.manufacturer.application.service.BrandAuthImportService;
import com.xiyu.bid.brandauth.manufacturer.application.service.BrandAuthExportService;
import com.xiyu.bid.brandauth.manufacturer.application.service.CreateManufacturerAuthAppService;
import com.xiyu.bid.brandauth.manufacturer.application.service.ListManufacturerAuthAppService;
import com.xiyu.bid.brandauth.manufacturer.application.service.RevokeManufacturerAuthAppService;
import com.xiyu.bid.brandauth.manufacturer.application.service.UpdateManufacturerAuthAppService;
import com.xiyu.bid.brandauth.manufacturer.application.service.AttachmentUploadAppService;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthOperationLogJpaRepository;
import com.xiyu.bid.exception.GlobalExceptionHandler;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 品牌授权导入入口 MockMvc 单元测试。
 * <p>覆盖 XIYU-R：确保 {@code POST /api/knowledge/brand-auth/import}
 * 在 multipart/form-data 请求下可被 Spring 正确路由并解析。</p>
 */
class ManufacturerAuthorizationControllerImportTest {

    private MockMvc mockMvc;
    private BrandAuthImportService importService;

    @BeforeEach
    void setup() {
        importService = mock(BrandAuthImportService.class);
        BrandAuthImportService.ImportResult emptyResult = new BrandAuthImportService.ImportResult();
        when(importService.importExcel(any(), any())).thenReturn(emptyResult);

        UserRepository userRepository = mock(UserRepository.class);
        com.xiyu.bid.entity.User adminUser = com.xiyu.bid.entity.User.builder().id(999L).username("admin").build();
        when(userRepository.findByUsername("admin")).thenReturn(java.util.Optional.of(adminUser));

        ManufacturerAuthorizationController controller = new ManufacturerAuthorizationController(
                mock(CreateManufacturerAuthAppService.class),
                mock(UpdateManufacturerAuthAppService.class),
                mock(RevokeManufacturerAuthAppService.class),
                mock(ListManufacturerAuthAppService.class),
                mock(AttachmentUploadAppService.class),
                mock(BrandAuthExportService.class),
                importService,
                userRepository,
                mock(BrandAuthOperationLogJpaRepository.class)
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UserDetails principal = User.withUsername("admin").password("x").roles("ADMIN").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAcceptMultipartImportRequest() throws Exception {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "brand-auth.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{0x50, 0x4B, 0x03, 0x04}); // minimal ZIP/XLSX magic bytes

        // when & then: 如果 consumes 未声明 multipart 或 Filter 破坏 input stream，
        // 这里会抛 400 MultipartException 而不是 200。
        mockMvc.perform(multipart("/api/knowledge/brand-auth/import").file(file))
                .andExpect(status().isOk());
    }
}
