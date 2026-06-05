package com.xiyu.bid.task.controller;

import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.task.dto.TaskExtendedFieldDTO;
import com.xiyu.bid.task.service.TaskExtendedFieldService;
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
 * Web 层契约测试：确认 GET /api/task-extended-fields 按 service 返回顺序
 * 输出 TaskExtendedFieldDTO 列表，字段名 {@code key}（非 fieldKey）供前端直接消费，
 * 且 {@code options} 已在服务端反序列化为结构化数组。
 */
@WebMvcTest(controllers = TaskExtendedFieldController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                        ApiKeyAuthenticationFilter.class}
        ))
@AutoConfigureMockMvc(addFilters = false)
class TaskExtendedFieldControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskExtendedFieldService service;

    @Test
    @WithMockUser(roles = "STAFF")
    void listsEnabledFields() throws Exception {
        when(service.listEnabled()).thenReturn(List.of(
                new TaskExtendedFieldDTO(
                        "customer_code",
                        "客户编号",
                        "text",
                        true,
                        "请输入客户编号",
                        List.of(),
                        10
                ),
                new TaskExtendedFieldDTO(
                        "delivery_date",
                        "交付日期",
                        "date",
                        false,
                        null,
                        List.of(),
                        20
                )
        ));

        mockMvc.perform(get("/api/task-extended-fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].key").value("customer_code"))
                .andExpect(jsonPath("$.data[0].label").value("客户编号"))
                .andExpect(jsonPath("$.data[0].fieldType").value("text"))
                .andExpect(jsonPath("$.data[0].required").value(true))
                .andExpect(jsonPath("$.data[0].placeholder").value("请输入客户编号"))
                .andExpect(jsonPath("$.data[0].sortOrder").value(10))
                .andExpect(jsonPath("$.data[1].key").value("delivery_date"))
                .andExpect(jsonPath("$.data[1].fieldType").value("date"))
                .andExpect(jsonPath("$.data[1].required").value(false));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void serializesOptionsInResponse() throws Exception {
        when(service.listEnabled()).thenReturn(List.of(
                new TaskExtendedFieldDTO(
                        "priority_level",
                        "优先级",
                        "select",
                        true,
                        null,
                        List.of(
                                new TaskExtendedFieldDTO.OptionItem("高", "HIGH"),
                                new TaskExtendedFieldDTO.OptionItem("中", "MEDIUM"),
                                new TaskExtendedFieldDTO.OptionItem("低", "LOW")
                        ),
                        30
                )
        ));

        mockMvc.perform(get("/api/task-extended-fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].key").value("priority_level"))
                .andExpect(jsonPath("$.data[0].fieldType").value("select"))
                .andExpect(jsonPath("$.data[0].options[0].label").value("高"))
                .andExpect(jsonPath("$.data[0].options[0].value").value("HIGH"))
                .andExpect(jsonPath("$.data[0].options[1].label").value("中"))
                .andExpect(jsonPath("$.data[0].options[2].value").value("LOW"));
    }
}
