package com.xiyu.bid.task.controller;

import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.task.dto.TaskStatusDictDTO;
import com.xiyu.bid.task.service.TaskStatusDictService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web 层契约测试：确认 GET /api/task-status-dict 按 service 返回顺序
 * 输出 TaskStatusDictDTO 列表，并将 category 序列化为枚举名字符串，
 * 满足前端 CHECK 语义（无需理解 Java 枚举）。
 */
@WebMvcTest(controllers = TaskStatusDictController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                        ApiKeyAuthenticationFilter.class}
        ))
@AutoConfigureMockMvc(addFilters = false)
class TaskStatusDictControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskStatusDictService taskStatusDictService;

    @Test
    @WithMockUser(roles = "STAFF")
    void listsEnabledStatuses() throws Exception {
        when(taskStatusDictService.listEnabled()).thenReturn(List.of(
                new TaskStatusDictDTO("TODO", "待办", "OPEN", "#909399", 10, true, false),
                new TaskStatusDictDTO("COMPLETED", "已完成", "CLOSED", "#67C23A", 90, false, true)
        ));

        mockMvc.perform(get("/api/task-status-dict"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("TODO"))
                .andExpect(jsonPath("$.data[0].name").value("待办"))
                .andExpect(jsonPath("$.data[0].category").value("OPEN"))
                .andExpect(jsonPath("$.data[0].color").value("#909399"))
                .andExpect(jsonPath("$.data[0].sortOrder").value(10))
                .andExpect(jsonPath("$.data[0].initial").value(true))
                .andExpect(jsonPath("$.data[0].terminal").value(false))
                .andExpect(jsonPath("$.data[1].code").value("COMPLETED"))
                .andExpect(jsonPath("$.data[1].category").value("CLOSED"))
                .andExpect(jsonPath("$.data[1].terminal").value(true));
    }
}
