// Input: mocked MentionApplicationService, AuthService, simulated UserDetails principal
// Output: MentionController HTTP contract checks aligned with production principal flow
// Pos: Test/提及控制器契约
package com.xiyu.bid.mention.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.mention.dto.CreateMentionRequest;
import com.xiyu.bid.mention.service.MentionApplicationService;
import com.xiyu.bid.mention.service.MentionApplicationService.MentionResult;
import com.xiyu.bid.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("MentionController endpoint contract")
class MentionControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MentionApplicationService service;

    @Mock
    private AuthService authService;

    @InjectMocks
    private MentionController controller;

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
    @DisplayName("POST /api/mentions returns wrapped mentionCount envelope")
    void createMention_ReturnsWrapped() throws Exception {
        when(service.createMention(any(CreateMentionRequest.class), anyLong()))
            .thenReturn(new MentionResult(2, 101L));

        String body = objectMapper.writeValueAsString(new CreateMentionRequest(
            "hi @[a](3) and @[b](4)", "comment", 42L, "Comment"));

        mockMvc.perform(post("/api/mentions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.mentionCount").value(2))
            .andExpect(jsonPath("$.data.notificationId").value(101));
    }

    @Test
    @DisplayName("POST /api/mentions with null notificationId returns 0")
    void createMention_NullNotificationId_ReturnsZero() throws Exception {
        when(service.createMention(any(CreateMentionRequest.class), anyLong()))
            .thenReturn(new MentionResult(0, null));

        String body = objectMapper.writeValueAsString(new CreateMentionRequest(
            "no mentions here", "comment", 42L, "Comment"));

        mockMvc.perform(post("/api/mentions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.mentionCount").value(0))
            .andExpect(jsonPath("$.data.notificationId").value(0));
    }
}
