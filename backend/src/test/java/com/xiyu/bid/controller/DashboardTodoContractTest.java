package com.xiyu.bid.controller;

import com.xiyu.bid.entity.Task;
import com.xiyu.bid.task.dto.TaskAssignmentCandidateDTO;
import com.xiyu.bid.task.dto.TaskDTO;
import com.xiyu.bid.task.dto.TeamTaskWorkloadDTO;
import com.xiyu.bid.task.service.TaskActivityService;
import com.xiyu.bid.task.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardTodoContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    @MockBean
    private TaskActivityService taskActivityService;

    @Test
    @WithMockUser(username = "alice", roles = {"STAFF"})
    void getMyTasks_ShouldReturnApiModeTodoPayload() throws Exception {
        TaskDTO task = TaskDTO.builder()
                .id(101L)
                .projectId(88L)
                .title("完成技术方案终稿")
                .description("补齐投标技术方案并提交评审")
                .assigneeId(7L)
                .status(Task.Status.TODO)
                .priority(Task.Priority.HIGH)
                .dueDate(LocalDateTime.of(2026, 3, 20, 18, 0))
                .build();

        when(taskService.getAccessibleTasksByAssigneeId(7L, "alice")).thenReturn(List.of(task));

        mockMvc.perform(get("/api/tasks/my").param("assigneeId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(101))
                .andExpect(jsonPath("$.data[0].title").value("完成技术方案终稿"))
                .andExpect(jsonPath("$.data[0].status").value("TODO"));
    }

    @Test
    @WithMockUser(username = "alice", roles = {"STAFF"})
    void updateTaskStatus_ShouldReturnCompletedTodoPayload() throws Exception {
        TaskDTO updatedTask = TaskDTO.builder()
                .id(101L)
                .projectId(88L)
                .title("完成技术方案终稿")
                .assigneeId(7L)
                .status(Task.Status.COMPLETED)
                .priority(Task.Priority.HIGH)
                .build();

        when(taskService.updateTaskStatus(101L, Task.Status.COMPLETED, "alice")).thenReturn(updatedTask);

        mockMvc.perform(patch("/api/tasks/101/status")
                        .contentType(APPLICATION_JSON)
                        .content("\"COMPLETED\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        verify(taskService).updateTaskStatus(eq(101L), eq(Task.Status.COMPLETED), eq("alice"));
    }

    @Test
    @WithMockUser(username = "manager", roles = {"MANAGER"})
    void getTeamWorkload_ShouldNotBeHandledAsTaskId() throws Exception {
        TeamTaskWorkloadDTO workload = TeamTaskWorkloadDTO.builder()
                .scope("部门团队")
                .orgConfigured(true)
                .members(List.of(TeamTaskWorkloadDTO.TeamMemberWorkloadDTO.builder()
                        .userId(7L)
                        .name("王工")
                        .deptCode("TECH")
                        .deptName("技术部")
                        .workloadScore(6)
                        .workloadLevel("medium")
                        .build()))
                .build();

        when(taskService.getTeamTaskWorkload("manager")).thenReturn(workload);

        mockMvc.perform(get("/api/tasks/team-workload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.members[0].userId").value(7))
                .andExpect(jsonPath("$.data.members[0].workloadLevel").value("medium"));
    }

    @Test
    @WithMockUser(username = "manager", roles = {"MANAGER"})
    void getAssignmentCandidates_ShouldNotBeHandledAsTaskId() throws Exception {
        TaskAssignmentCandidateDTO candidate = TaskAssignmentCandidateDTO.builder()
                .userId(8L)
                .name("张经理")
                .deptCode("BID")
                .deptName("投标管理部")
                .roleCode("bid_manager")
                .roleName("投标经理")
                .enabled(true)
                .build();

        when(taskService.getAssignmentCandidates("BID", "bid_manager", "manager"))
                .thenReturn(List.of(candidate));

        mockMvc.perform(get("/api/tasks/assignment-candidates")
                        .param("deptCode", "BID")
                        .param("roleCode", "bid_manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].userId").value(8))
                .andExpect(jsonPath("$.data[0].roleCode").value("bid_manager"));
    }
}
