package com.xiyu.bid.task.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.task.dto.TaskStatusDictAdminDTO;
import com.xiyu.bid.task.dto.TaskStatusDictReorderRequest;
import com.xiyu.bid.task.dto.TaskStatusDictUpsertRequest;
import com.xiyu.bid.task.entity.TaskStatusCategory;
import com.xiyu.bid.task.service.TaskStatusDictAdminService;
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
 * Web 层契约测试：确认 /api/admin/task-status-dict 6 个端点的 wire/contract。
 *
 * <p>角色门禁通过 {@code @PreAuthorize("hasRole('ADMIN')")} 实现，但本测试通过
 * {@code addFilters = false} 跳过过滤器，仅校验路由 + 序列化契约。真正的角色
 * 拒绝路径由集成测试 / E2E 覆盖。</p>
 */
@WebMvcTest(controllers = TaskStatusDictAdminController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                        ApiKeyAuthenticationFilter.class}
        ))
@AutoConfigureMockMvc(addFilters = false)
class TaskStatusDictAdminControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TaskStatusDictAdminService service;

    @Autowired
    private ObjectMapper json;

    private TaskStatusDictAdminDTO sample(String code) {
        return new TaskStatusDictAdminDTO(
                code,
                "x",
                "OPEN",
                "#909399",
                10,
                false,
                false,
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list() throws Exception {
        when(service.listAll()).thenReturn(List.of(sample("TODO")));
        mvc.perform(get("/api/admin/task-status-dict"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("TODO"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create() throws Exception {
        when(service.create(any())).thenReturn(sample("ARCHIVED"));
        TaskStatusDictUpsertRequest req = new TaskStatusDictUpsertRequest();
        req.setCode("ARCHIVED");
        req.setName("已归档");
        req.setCategory(TaskStatusCategory.CLOSED);
        mvc.perform(post("/api/admin/task-status-dict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("ARCHIVED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update() throws Exception {
        when(service.update(eq("TODO"), any())).thenReturn(sample("TODO"));
        TaskStatusDictUpsertRequest req = new TaskStatusDictUpsertRequest();
        req.setCode("TODO");
        req.setName("待办（更新）");
        req.setCategory(TaskStatusCategory.OPEN);
        mvc.perform(put("/api/admin/task-status-dict/TODO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("TODO"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void disable() throws Exception {
        when(service.disable("REVIEW")).thenReturn(sample("REVIEW"));
        mvc.perform(patch("/api/admin/task-status-dict/REVIEW/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("REVIEW"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void enable() throws Exception {
        when(service.enable("REVIEW")).thenReturn(sample("REVIEW"));
        mvc.perform(patch("/api/admin/task-status-dict/REVIEW/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("REVIEW"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reorder() throws Exception {
        TaskStatusDictReorderRequest req = new TaskStatusDictReorderRequest();
        req.setItems(List.of(new TaskStatusDictReorderRequest.Item("TODO", 100)));
        mvc.perform(patch("/api/admin/task-status-dict/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
