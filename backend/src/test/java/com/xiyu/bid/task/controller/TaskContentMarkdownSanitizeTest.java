package com.xiyu.bid.task.controller;

import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.task.dto.TaskDTO;
import com.xiyu.bid.task.service.TaskActivityService;
import com.xiyu.bid.task.service.TaskService;
import com.xiyu.bid.user.service.AssignmentCandidateAppService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web 层契约测试：确认 POST /api/tasks 创建任务时，Markdown 结构字符
 * ({@code \t \n \r}) 不会被 sanitize 阶段剥离；同时真正危险的 ASCII
 * 控制字符（例如 0x07 BEL）仍会被剥离。
 *
 * <p>回归保护：防止 {@link com.xiyu.bid.util.InputSanitizer#sanitizeString}
 * 路径再次被意外复用于 Markdown 字段导致行分隔符丢失。</p>
 */
@WebMvcTest(controllers = TaskController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                        ApiKeyAuthenticationFilter.class}
        ))
@AutoConfigureMockMvc(addFilters = false)
class TaskContentMarkdownSanitizeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    @MockBean
    private TaskActivityService taskActivityService;

    @MockBean
    private AssignmentCandidateAppService assignmentCandidateAppService;

    @Test
    @WithMockUser(roles = "MANAGER")
    void markdownLineBreaksSurviveSanitize() throws Exception {
        ArgumentCaptor<TaskDTO> captor = ArgumentCaptor.forClass(TaskDTO.class);
        when(taskService.createTask(any(TaskDTO.class))).thenAnswer(inv -> {
            TaskDTO dto = inv.getArgument(0);
            dto.setId(1L);
            return dto;
        });

        // NOTE: content 字段在 JSON 中使用 \n 转义（即两字符："\\" + "n"），
        // Jackson 在反序列化后得到真正的 0x0A。这里用 Java 文本块避免手工拼接。
        String body = """
                {
                  "title": "T",
                  "projectId": 1,
                  "status": "TODO",
                  "content": "# 任务详情\\n- 步骤 1\\n- 步骤 2\\n```java\\nint x=1;\\n```"
                }
                """;

        mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        org.mockito.Mockito.verify(taskService).createTask(captor.capture());
        String captured = captor.getValue().getContent();
        assertThat(captured)
                .as("Markdown \\n 必须在 sanitize 之后保留")
                .contains("\n");
        assertThat(captured).contains("# 任务详情");
        assertThat(captured).contains("- 步骤 1");
        assertThat(captured).contains("- 步骤 2");
        assertThat(captured).contains("```java");
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void dangerousControlCharsStrippedButNewlinesKept() throws Exception {
        ArgumentCaptor<TaskDTO> captor = ArgumentCaptor.forClass(TaskDTO.class);
        when(taskService.createTask(any(TaskDTO.class))).thenAnswer(inv -> {
            TaskDTO dto = inv.getArgument(0);
            dto.setId(1L);
            return dto;
        });

        // 0x07 (bell) 必须被剥离；\n 必须保留。
        String body = "{\"title\":\"T\",\"projectId\":1,\"status\":\"TODO\","
                + "\"content\":\"a\\u0007b\\nc\"}";

        mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        org.mockito.Mockito.verify(taskService).createTask(captor.capture());
        String captured = captor.getValue().getContent();
        // 期望：0x07 (BEL) 被剥掉、\n 保留 → "ab\nc"
        assertThat(captured).isEqualTo("ab\nc");
    }
}
