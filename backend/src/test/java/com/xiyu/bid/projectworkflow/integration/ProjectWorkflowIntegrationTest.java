package com.xiyu.bid.projectworkflow.integration;

import com.xiyu.bid.biddraftagent.entity.BidRequirementItem;
import com.xiyu.bid.biddraftagent.entity.BidTenderDocumentSnapshot;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.projectworkflow.dto.ProjectReminderCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectShareLinkCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskStatusUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class ProjectWorkflowIntegrationTest extends AbstractProjectWorkflowIntegrationTest {

    @SpyBean
    private com.xiyu.bid.repository.TaskRepository taskRepositorySpy;

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void projectWorkflowEndpoints_ShouldPersistTasksDocumentsRemindersAndShareLinks() throws Exception {
        ProjectTaskCreateRequest taskRequest = ProjectTaskCreateRequest.builder()
                .title("准备商务应答")
                .description("整理商务偏离表")
                .content("# 商务应答\n- 整理商务偏离表")
                .assigneeId(ownerUser.getId())
                .assigneeName("李总")
                .priority(ProjectTaskCreateRequest.Priority.HIGH)
                .dueDate(LocalDateTime.of(2026, 3, 15, 18, 0))
                .build();

        String taskResponse = mockMvc.perform(post("/api/projects/{projectId}/tasks", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("准备商务应答"))
                .andExpect(jsonPath("$.data.content").value("# 商务应答\n- 整理商务偏离表"))
                .andExpect(jsonPath("$.data.owner").value("李总"))
                .andExpect(jsonPath("$.data.status").value("todo"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long taskId = objectMapper.readTree(taskResponse).path("data").path("id").asLong();

        ProjectTaskStatusUpdateRequest statusRequest = ProjectTaskStatusUpdateRequest.builder()
                .status(ProjectTaskStatusUpdateRequest.Status.IN_PROGRESS)
                .build();

        mockMvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/status", project.getId(), taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("doing"));

        mockMvc.perform(get("/api/projects/{projectId}/tasks", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("准备商务应答"))
                .andExpect(jsonPath("$.data[0].owner").value("李总"));

        Long documentId = documentApiSupport.createProjectDocument(
                        project.getId(),
                        ownerUser,
                        "PROJECT_DELIVERABLE",
                        "TASK",
                        8801L,
                        "商务应答.docx",
                        "2MB",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "https://files.example.com/bid/notice.docx")
                .path("id")
                .asLong();

        mockMvc.perform(get("/api/projects/{projectId}/documents", project.getId())
                        .param("documentCategory", "PROJECT_DELIVERABLE")
                        .param("linkedEntityType", "TASK")
                        .param("linkedEntityId", "8801"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("商务应答.docx"))
                .andExpect(jsonPath("$.data[0].documentCategory").value("PROJECT_DELIVERABLE"));

        ProjectReminderCreateRequest reminderRequest = ProjectReminderCreateRequest.builder()
                .title("跟进商务稿")
                .message("明天 09:00 前确认商务应答版本")
                .remindAt(LocalDateTime.of(2026, 3, 12, 9, 0))
                .createdBy(ownerUser.getId())
                .createdByName("李总")
                .recipient("项目负责人")
                .build();

        mockMvc.perform(post("/api/projects/{projectId}/reminders", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reminderRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("跟进商务稿"))
                .andExpect(jsonPath("$.data.createdByName").value("李总"));

        ProjectShareLinkCreateRequest shareRequest = ProjectShareLinkCreateRequest.builder()
                .createdBy(ownerUser.getId())
                .createdByName("李总")
                .baseUrl("http://127.0.0.1:14173")
                .build();

        mockMvc.perform(post("/api/projects/{projectId}/share-links", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.containsString("/project/" + project.getId())))
                .andExpect(jsonPath("$.data.createdByName").value("李总"));

        mockMvc.perform(delete("/api/projects/{projectId}/documents/{documentId}", project.getId(), documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(taskRepository.findByProjectId(project.getId())).hasSize(1);
        assertThat(taskRepository.findByProjectId(project.getId()).get(0).getStatus()).isEqualTo(Task.Status.IN_PROGRESS);
        assertThat(projectDocumentRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).isEmpty();
        assertThat(projectReminderRepository.findByProjectIdOrderByRemindAtDesc(project.getId())).hasSize(1);
        assertThat(projectShareLinkRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).hasSize(1);
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void decomposeProjectTasks_ShouldCreateTasksFromParsedTenderRequirements() throws Exception {
        bidTenderDocumentSnapshotRepository.save(BidTenderDocumentSnapshot.builder()
                .projectId(project.getId())
                .tenderId(project.getTenderId())
                .projectDocumentId(7001L)
                .fileName("招标文件.docx")
                .extractedText("技术实施方案要求")
                .profileJson("{}")
                .extractorKey("test")
                .analyzerKey("test")
                .build());
        bidRequirementItemRepository.save(BidRequirementItem.builder()
                .projectId(project.getId())
                .tenderId(project.getTenderId())
                .projectDocumentId(7001L)
                .category("technical")
                .title("技术实施方案")
                .content("提交平台对接和实施计划")
                .mandatory(true)
                .confidence(90)
                .build());

        mockMvc.perform(post("/api/projects/{projectId}/tasks/decompose", project.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("技术标：技术实施方案"))
                .andExpect(jsonPath("$.data[0].status").value("todo"));

        assertThat(taskRepository.findByProjectId(project.getId()))
                .extracting(Task::getTitle)
                .containsExactly("技术标：技术实施方案");
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void decomposeProjectTasks_ShouldRollbackAllTasksWhenSavingFailsMidway() throws Exception {
        bidTenderDocumentSnapshotRepository.save(BidTenderDocumentSnapshot.builder()
                .projectId(project.getId())
                .tenderId(project.getTenderId())
                .projectDocumentId(7002L)
                .fileName("招标文件.docx")
                .extractedText("商务和技术要求")
                .profileJson("{}")
                .extractorKey("test")
                .analyzerKey("test")
                .build());
        bidRequirementItemRepository.save(BidRequirementItem.builder()
                .projectId(project.getId())
                .tenderId(project.getTenderId())
                .projectDocumentId(7002L)
                .category("commercial")
                .title("商务条款响应")
                .content("完成商务偏离表")
                .mandatory(true)
                .confidence(90)
                .build());
        bidRequirementItemRepository.save(BidRequirementItem.builder()
                .projectId(project.getId())
                .tenderId(project.getTenderId())
                .projectDocumentId(7002L)
                .category("technical")
                .title("技术实施方案")
                .content("完成技术实施计划")
                .mandatory(true)
                .confidence(90)
                .build());
        AtomicInteger saveCount = new AtomicInteger();
        doAnswer(invocation -> {
            if (saveCount.incrementAndGet() == 2) {
                throw new IllegalStateException("模拟第二个任务保存失败");
            }
            return invocation.callRealMethod();
        }).when(taskRepositorySpy).save(any(Task.class));

        mockMvc.perform(post("/api/projects/{projectId}/tasks/decompose", project.getId()))
                .andExpect(status().is5xxServerError());

        assertThat(taskRepository.findByProjectId(project.getId())).isEmpty();
    }
}
