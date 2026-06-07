package com.xiyu.bid.export.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.config.ExportConfig;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.export.dto.ExportRequest;
import com.xiyu.bid.export.dto.ExportResponse;
import com.xiyu.bid.export.service.ExcelExportService;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportControllerSecurityTest {

    @Mock
    private AuthService authService;

    @Mock
    private ExcelExportService excelExportService;

    @Mock
    private RateLimitService rateLimitService;

    @Test
    void extractUserId_ResolvesPersistedUserByUsername() throws Exception {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.withUsername("alice")
                .password("password")
                .authorities("ROLE_STAFF")
                .build();
        User user = User.builder()
                .id(7L)
                .username("alice")
                .role(User.Role.STAFF)
                .email("alice@example.com")
                .fullName("Alice")
                .password("secret")
                .enabled(true)
                .build();

        org.mockito.Mockito.when(authService.resolveUserByUsername("alice")).thenReturn(user);

        Method method = ExportController.class.getDeclaredMethod("extractUserId", UserDetails.class);
        method.setAccessible(true);

        ExportController controller = new ExportController(
                null,
                (ExportConfig) null,
                (RateLimitService) null,
                new ObjectMapper(),
                authService
        );

        Long userId = (Long) method.invoke(controller, userDetails);

        assertThat(userId).isEqualTo(7L);
    }

    @Test
    void exportToExcel_ReturnsRecordCountFromServiceResult() throws Exception {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.withUsername("alice")
                .password("password")
                .authorities("ROLE_STAFF")
                .build();
        User user = User.builder()
                .id(7L)
                .username("alice")
                .role(User.Role.STAFF)
                .email("alice@example.com")
                .fullName("Alice")
                .password("secret")
                .enabled(true)
                .build();

        when(authService.resolveUserByUsername("alice")).thenReturn(user);
        when(rateLimitService.checkExportRateLimit(7L)).thenReturn(true);
        when(excelExportService.getExportFileName("tenders")).thenReturn("tenders.xlsx");
        when(excelExportService.exportToExcelWithResult(eq("tenders"), any(Path.class), eq("{}"), eq(7L)))
                .thenReturn(new ExcelExportService.ExportFileResult(2048L, 3));
        ExportController controller = new ExportController(
                excelExportService,
                (ExportConfig) null,
                rateLimitService,
                new ObjectMapper(),
                authService
        );
        ExportRequest request = ExportRequest.builder()
                .dataType("tenders")
                .params(Map.of())
                .async(false)
                .build();

        ResponseEntity<?> response = controller.exportToExcel(request, userDetails);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(ApiResponse.class);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getData()).isInstanceOf(ExportResponse.class);
        ExportResponse exportResponse = (ExportResponse) body.getData();
        assertThat(exportResponse.getFileSize()).isEqualTo(2048L);
        assertThat(exportResponse.getRecordCount()).isEqualTo(3);
    }

    @Test
    void extractUserId_RejectsUnknownAuthenticatedUser() throws Exception {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.withUsername("ghost")
                .password("password")
                .authorities("ROLE_STAFF")
                .build();

        org.mockito.Mockito.when(authService.resolveUserByUsername("ghost"))
                .thenThrow(new UsernameNotFoundException("User not found"));

        Method method = ExportController.class.getDeclaredMethod("extractUserId", UserDetails.class);
        method.setAccessible(true);

        ExportController controller = new ExportController(
                null,
                (ExportConfig) null,
                (RateLimitService) null,
                new ObjectMapper(),
                authService
        );

        assertThatThrownBy(() -> method.invoke(controller, userDetails))
                .hasCauseExactlyInstanceOf(AuthenticationServiceException.class)
                .cause()
                .hasMessageContaining("Authenticated user not found");
    }
}
