// Input: mocked UserSearchService, simulated AuthenticationPrincipal
// Output: UserSearchController HTTP contract checks
// Pos: Test/用户搜索控制器契约
package com.xiyu.bid.mention.controller;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.mention.dto.UserSearchResult;
import com.xiyu.bid.mention.service.UserSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserSearchController endpoint contract")
class UserSearchControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserSearchService searchService;

    @InjectMocks
    private UserSearchController controller;

    private static final User TEST_USER = User.builder()
        .id(7L).username("alice").email("a@x.com").fullName("Alice").password("p")
        .role(User.Role.MANAGER).build();

    @BeforeEach
    void setUp() {
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
                    return TEST_USER;
                }
            })
            .build();
    }

    @Test
    @DisplayName("GET /api/users/search returns wrapped data envelope")
    void search_ReturnsWrapped() throws Exception {
        when(searchService.search(eq("ali"), any())).thenReturn(List.of(
            new UserSearchResult(3L, "Alice", null, "MANAGER", null, "bid-projectLeader")));

        mockMvc.perform(get("/api/users/search").param("q", "ali"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(3))
            .andExpect(jsonPath("$.data[0].name").value("Alice"))
            .andExpect(jsonPath("$.data[0].role").value("MANAGER"))
            .andExpect(jsonPath("$.data[0].roleCode").value("bid-projectLeader"));
    }

    @Test
    @DisplayName("GET /api/users/search defaults q to empty and returns ok")
    void search_DefaultsMissingQuery() throws Exception {
        when(searchService.search(eq(""), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/users/search"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
        verify(searchService).search(eq(""), any());
    }

    @Test
    @DisplayName("GET /api/users/search passes limit query param to service")
    void search_PassesLimit() throws Exception {
        when(searchService.search(eq("a"), eq(5))).thenReturn(List.of());

        mockMvc.perform(get("/api/users/search").param("q", "a").param("limit", "5"))
            .andExpect(status().isOk());
        verify(searchService).search(eq("a"), eq(5));
    }
}
