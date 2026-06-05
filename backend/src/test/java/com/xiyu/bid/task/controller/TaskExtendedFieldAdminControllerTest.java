package com.xiyu.bid.task.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.task.dto.TaskExtendedFieldAdminDTO;
import com.xiyu.bid.task.dto.TaskExtendedFieldReorderRequest;
import com.xiyu.bid.task.dto.TaskExtendedFieldUpsertRequest;
import com.xiyu.bid.task.entity.TaskExtendedFieldType;
import com.xiyu.bid.task.service.TaskExtendedFieldAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web 层契约测试：确认 /api/admin/task-extended-fields 6 个端点的 wire/contract。
 *
 * <p>镜像 {@link TaskStatusDictAdminControllerTest}：通过
 * {@code addFilters = false} 跳过过滤器，仅校验路由 + 序列化契约。角色拒绝
 * 路径（ADMIN 以外返回 403）由集成测试 / E2E 覆盖。</p>
 */
@WebMvcTest(controllers = TaskExtendedFieldAdminController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                        ApiKeyAuthenticationFilter.class}
        ))
@AutoConfigureMockMvc(addFilters = false)
class TaskExtendedFieldAdminControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TaskExtendedFieldAdminService service;

    @Autowired
    private ObjectMapper json;

    private TaskExtendedFieldAdminDTO sample(String key) {
        return new TaskExtendedFieldAdminDTO(
                key,
                "示例字段",
                "text",
                false,
                "请输入",
                List.of(),
                10,
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list() throws Exception {
        when(service.listAll()).thenReturn(List.of(sample("budget")));
        mvc.perform(get("/api/admin/task-extended-fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].key").value("budget"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create() throws Exception {
        when(service.create(any())).thenReturn(sample("budget"));
        TaskExtendedFieldUpsertRequest req = new TaskExtendedFieldUpsertRequest();
        req.setKey("budget");
        req.setLabel("预算");
        req.setFieldType(TaskExtendedFieldType.number);
        mvc.perform(post("/api/admin/task-extended-fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("budget"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update() throws Exception {
        when(service.update(eq("budget"), any())).thenReturn(sample("budget"));
        TaskExtendedFieldUpsertRequest req = new TaskExtendedFieldUpsertRequest();
        req.setKey("budget");
        req.setLabel("预算（更新）");
        req.setFieldType(TaskExtendedFieldType.number);
        mvc.perform(put("/api/admin/task-extended-fields/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("budget"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void disable() throws Exception {
        when(service.disable("budget")).thenReturn(sample("budget"));
        mvc.perform(patch("/api/admin/task-extended-fields/budget/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("budget"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void enable() throws Exception {
        when(service.enable("budget")).thenReturn(sample("budget"));
        mvc.perform(patch("/api/admin/task-extended-fields/budget/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("budget"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reorder() throws Exception {
        when(service.reorder(any())).thenReturn(List.of(sample("budget")));
        TaskExtendedFieldReorderRequest req = new TaskExtendedFieldReorderRequest();
        req.setItems(List.of(new TaskExtendedFieldReorderRequest.Item("budget", 100)));
        mvc.perform(patch("/api/admin/task-extended-fields/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].key").value("budget"));
    }
}
