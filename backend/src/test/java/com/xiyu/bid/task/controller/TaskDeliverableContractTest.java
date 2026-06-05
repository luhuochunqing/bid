package com.xiyu.bid.task.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.projectworkflow.controller.ProjectWorkflowController;
import com.xiyu.bid.task.core.BidSubmissionPolicy;
import com.xiyu.bid.task.dto.BidSubmissionResponse;
import com.xiyu.bid.task.dto.DeliverableCoverageDTO;
import com.xiyu.bid.task.dto.TaskDeliverableCreateRequest;
import com.xiyu.bid.task.dto.TaskDeliverableDTO;
import com.xiyu.bid.task.service.BidProcessService;
import com.xiyu.bid.task.service.TaskDeliverableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TaskDeliverableContractTest {

    @Mock
    private TaskDeliverableService taskDeliverableService;

    @Mock
    private BidProcessService bidProcessService;

    @Mock
    private ProjectWorkflowController projectWorkflowController; // we'll use standalone setup

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProjectWorkflowController(
                null, null, taskDeliverableService, bidProcessService))
                .setControllerAdvice(new com.xiyu.bid.exception.GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void getTaskDeliverables_ShouldReturnList() throws Exception {
        var dto = TaskDeliverableDTO.builder().id(1L).taskId(10L).name("资质证书").deliverableType("QUALIFICATION").build();
        when(taskDeliverableService.getDeliverablesByTaskId(1L, 10L)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/projects/1/tasks/10/deliverables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("资质证书"));
    }

    @Test
    void createTaskDeliverable_ShouldReturnCreated() throws Exception {
        var dto = TaskDeliverableDTO.builder().id(1L).taskId(10L).name("技术方案").version(1).build();
        when(taskDeliverableService.createDeliverable(any(), any(), any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/projects/1/tasks/10/deliverables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskDeliverableCreateRequest("技术方案", "TECHNICAL", null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("技术方案"));
    }

    @Test
    void createTaskDeliverable_ShouldAcceptUploadedFileUrl() throws Exception {
        var dto = TaskDeliverableDTO.builder()
                .id(1L)
                .taskId(10L)
                .name("技术方案")
                .url("project-documents://1/task/10/技术方案.docx")
                .version(1)
                .build();
        when(taskDeliverableService.createDeliverable(any(), any(), any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/projects/1/tasks/10/deliverables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "技术方案",
                                  "deliverableType": "TECHNICAL",
                                  "url": "project-documents://1/task/10/技术方案.docx"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").value("project-documents://1/task/10/技术方案.docx"));

        var captor = org.mockito.ArgumentCaptor.forClass(TaskDeliverableCreateRequest.class);
        verify(taskDeliverableService).createDeliverable(eq(1L), eq(10L), captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getUrl())
                .isEqualTo("project-documents://1/task/10/技术方案.docx");
    }

    @Test
    void deleteTaskDeliverable_ShouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/api/projects/1/tasks/10/deliverables/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getDeliverableCoverage_ShouldReturnCoverage() throws Exception {
        var coverage = DeliverableCoverageDTO.builder()
                .taskId(10L).requiredCount(2).coveredCount(1).percentage(50.0).build();
        when(taskDeliverableService.getDeliverableCoverage(10L, null)).thenReturn(coverage);

        mockMvc.perform(get("/api/projects/1/tasks/10/deliverables/coverage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requiredCount").value(2));
    }

    @Test
    void submitToBidDocument_Accepted() throws Exception {
        var response = BidSubmissionResponse.builder()
                .accepted(true).message("已提交至标书编写流程")
                .totalTasks(3).completedTasks(3).tasksWithDeliverables(2).build();
        when(bidProcessService.submitToBidDocument(1L)).thenReturn(response);

        mockMvc.perform(post("/api/projects/1/submit-to-bid-document"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accepted").value(true));
    }

    @Test
    void submitToBidDocument_Rejected() throws Exception {
        var response = BidSubmissionResponse.builder()
                .accepted(false).message("提交校验未通过")
                .totalTasks(3).completedTasks(2).tasksWithDeliverables(0)
                .gaps(List.of(new BidSubmissionPolicy.TaskGap(null, null, "有 1 个任务未完成"))).build();
        when(bidProcessService.submitToBidDocument(1L)).thenReturn(response);

        mockMvc.perform(post("/api/projects/1/submit-to-bid-document"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(false));
    }
}
