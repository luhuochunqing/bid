// Input: HTTP requests against /api/subscriptions and /api/entities/*/subscription
// Output: controller contract coverage via standalone MockMvc with UserDetails principal
// Pos: Test/订阅控制器契约测试
package com.xiyu.bid.subscription.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.subscription.dto.SubscriptionRequest;
import com.xiyu.bid.subscription.dto.SubscriptionSummary;
import com.xiyu.bid.subscription.service.SubscriptionApplicationService;
import com.xiyu.bid.subscription.service.SubscriptionApplicationService.SubscribeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionController endpoint contract")
class SubscriptionControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SubscriptionApplicationService service;

    @Mock
    private AuthService authService;

    @InjectMocks
    private SubscriptionController controller;

    private static final User TEST_USER = User.builder()
        .id(7L).username("alice").email("a@x.com").fullName("Alice").password("p")
        .role(User.Role.STAFF).build();
    private static final UserDetails TEST_DETAILS = org.springframework.security.core.userdetails.User
        .withUsername("alice")
        .password("p")
        .authorities("ROLE_STAFF")
        .build();

    @BeforeEach
    void setUp() {
        when(authService.resolveUserByUsername("alice")).thenReturn(TEST_USER);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                }

                @Override
                public Object resolveArgument(MethodParameter parameter,
                    ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory) {
                    return TEST_DETAILS;
                }
            })
            .build();
    }

    @Test
    @DisplayName("POST /api/subscriptions valid returns 200 success")
    void subscribe_Valid_ReturnsOk() throws Exception {
        when(service.subscribe(eq(7L), eq("PROJECT"), eq(42L)))
            .thenReturn(SubscribeResult.ok(100L));

        String body = objectMapper.writeValueAsString(new SubscriptionRequest("PROJECT", 42L));

        mockMvc.perform(post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(service).subscribe(7L, "PROJECT", 42L);
    }

    @Test
    @DisplayName("POST /api/subscriptions invalid entityType returns 400")
    void subscribe_Invalid_ReturnsBadRequest() throws Exception {
        when(service.subscribe(anyLong(), anyString(), anyLong()))
            .thenReturn(SubscribeResult.error("INVALID_ENTITY_TYPE", "不支持的订阅类型"));

        String body = objectMapper.writeValueAsString(new SubscriptionRequest("FOOBAR", 42L));

        mockMvc.perform(post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DELETE /api/subscriptions returns 200")
    void unsubscribe_ReturnsOk() throws Exception {
        when(service.unsubscribe(7L, "PROJECT", 42L)).thenReturn(1);

        String body = objectMapper.writeValueAsString(new SubscriptionRequest("PROJECT", 42L));

        mockMvc.perform(delete("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(service).unsubscribe(7L, "PROJECT", 42L);
    }

    @Test
    @DisplayName("GET /api/subscriptions/me returns paged content")
    void listMine_ReturnsPage() throws Exception {
        SubscriptionSummary summary = new SubscriptionSummary(
            100L, "PROJECT", 42L, LocalDateTime.now());
        Page<SubscriptionSummary> page = new PageImpl<>(List.of(summary));
        when(service.listMySubscriptions(anyLong(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/subscriptions/me").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/entities/{type}/{id}/subscription returns subscribed flag")
    void checkSubscribed_ReturnsFlag() throws Exception {
        when(service.isSubscribed(7L, "PROJECT", 42L)).thenReturn(true);

        mockMvc.perform(get("/api/entities/PROJECT/42/subscription"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.subscribed").value(true));
    }
}
