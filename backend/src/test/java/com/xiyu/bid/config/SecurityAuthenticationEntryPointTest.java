package com.xiyu.bid.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证未登录或 Token 失效的匿名请求访问受保护 API 时，
 * 必须统一返回 401 Unauthorized（而不是 403 Forbidden），
 * 并携带标准 JSON 错误体，以便前端 client.js 拦截器触发重新登录或无感刷新。
 */
@SpringBootTest(properties = "management.health.redis.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuthenticationEntryPointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("匿名访问/api/projects返回401与JSON错误体")
    void unauthenticatedAccessToProjects_shouldReturn401Json() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("请重新登录")));
    }

    @Test
    @DisplayName("匿名访问/api/tenders返回401与JSON错误体")
    void unauthenticatedAccessToTenders_shouldReturn401Json() throws Exception {
        mockMvc.perform(get("/api/tenders"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("匿名访问/api/auth/me返回401与JSON错误体")
    void unauthenticatedAccessToAuthMe_shouldReturn401Json() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.success").value(false));
    }
}
