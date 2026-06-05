package com.xiyu.bid.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.NotificationDispatchPolicy.DispatchResult;
import com.xiyu.bid.notification.core.NotificationReadPolicy.ReadResult;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.dto.NotificationSummary;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.service.AuthService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationController endpoint contract")
class NotificationControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private NotificationApplicationService service;

    @Mock
    private AuthService authService;

    @InjectMocks
    private NotificationController controller;

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
    @DisplayName("GET /api/notifications returns paged summaries")
    void getNotifications_ReturnsPage() throws Exception {
        NotificationSummary summary = new NotificationSummary(
            11L, 100L, "INFO", "title", "body", "project", 42L, false, LocalDateTime.now());
        Page<NotificationSummary> page = new PageImpl<>(List.of(summary));
        when(service.getNotifications(anyLong(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/notifications").param("page", "0").param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/notifications/unread-count returns count")
    void getUnreadCount_ReturnsCount() throws Exception {
        when(service.getUnreadCount(7L)).thenReturn(4L);

        mockMvc.perform(get("/api/notifications/unread-count"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.count").value(4));
    }

    @Test
    @DisplayName("POST /api/notifications/{id}/read returns ok")
    void markAsRead_Ok() throws Exception {
        when(service.markAsRead(100L, 7L)).thenReturn(ReadResult.valid());

        mockMvc.perform(post("/api/notifications/100/read"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
        verify(service).markAsRead(100L, 7L);
    }

    @Test
    @DisplayName("POST /api/notifications/{id}/read returns 403 when forbidden")
    void markAsRead_Forbidden() throws Exception {
        when(service.markAsRead(100L, 7L)).thenReturn(ReadResult.forbidden());

        mockMvc.perform(post("/api/notifications/100/read"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/notifications/read-all calls service")
    void markAllAsRead_Ok() throws Exception {
        mockMvc.perform(post("/api/notifications/read-all"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
        verify(service).markAllAsRead(7L);
    }

    @Test
    @DisplayName("POST /api/admin/notifications creates notification")
    void createNotification_Ok() throws Exception {
        when(service.createNotification(any(CreateNotificationRequest.class), anyLong()))
            .thenReturn(DispatchResult.valid());

        String body = objectMapper.writeValueAsString(new CreateNotificationRequest(
            "INFO", null, null, "title", "hi", null, List.of(7L)));

        mockMvc.perform(post("/api/admin/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/admin/notifications returns 400 on dispatch error")
    void createNotification_BadRequest() throws Exception {
        when(service.createNotification(any(CreateNotificationRequest.class), anyLong()))
            .thenReturn(DispatchResult.invalid("INVALID_TITLE", "blank"));

        String body = objectMapper.writeValueAsString(new CreateNotificationRequest(
            "INFO", null, null, "title", "hi", null, List.of(7L)));

        mockMvc.perform(post("/api/admin/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }
}
