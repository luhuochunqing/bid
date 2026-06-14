package com.xiyu.bid.controller;

import com.xiyu.bid.dto.AuthResponse;
import com.xiyu.bid.dto.AuthSessionResult;
import com.xiyu.bid.dto.LoginRequest;
import com.xiyu.bid.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    void login_ShouldReturnAccessTokenAndRefreshCookie() throws Exception {
        // H13 根治 (2026-06-14): access token 不再放 body (token=null), 改走 HttpOnly cookie
        AuthResponse response = AuthResponse.builder()
                .type("Bearer")
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .fullName("Alice")
                .role("admin")
                .roleCode("admin")
                .roleName("管理员")
                .build();

        when(authService.login(eq(loginRequest("alice", "secret"))))
                .thenReturn(AuthSessionResult.builder()
                        .authResponse(response)
                        .refreshToken("refresh-token")
                        .accessToken("access-token")
                        .build());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=refresh-token")))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                                .anyMatch(c -> c.contains("access_token=access-token")),
                        "access_token cookie missing"))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.username").value("alice"));
    }

    @Test
    void loginPreflight_ShouldAllowTheLocalFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:1314")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type, authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:1314"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void logout_ShouldReturnSuccessResponseAndClearCookie() throws Exception {
        // H13 根治: access token 从 access_token cookie 读, logout 清除双 cookie
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "access-jwt"))
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                                .anyMatch(c -> c.contains("access_token=")),
                        "access_token cookie missing on logout"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.msg").value("Logout successful"));

        verify(authService).logout("access-jwt", "refresh-token");
    }

    @Test
    void refresh_ShouldIssueNewAccessTokenForRefreshCookie() throws Exception {
        // H13 根治: access token 不放 body, 走 cookie
        AuthResponse refreshResponse = AuthResponse.builder()
                .type("Bearer")
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .fullName("Alice")
                .role("admin")
                .roleCode("admin")
                .roleName("管理员")
                .build();

        when(authService.refreshToken(eq("refresh-token")))
                .thenReturn(AuthSessionResult.builder()
                        .authResponse(refreshResponse)
                        .refreshToken("rotated-refresh-token")
                        .accessToken("refreshed-token")
                        .build());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=rotated-refresh-token")))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                                .anyMatch(c -> c.contains("access_token=refreshed-token")),
                        "access_token cookie missing"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.msg").value("Token refreshed successfully"))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.username").value("alice"));
    }

    @Test
    void getCurrentUser_ShouldRejectUnauthorizedRequest() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logout_ShouldStillSucceedWithoutRefreshCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(authService).logout(isNull(), isNull());
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
