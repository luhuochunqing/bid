package com.xiyu.bid.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.admin.controller.AdminProjectGroupController;
import com.xiyu.bid.dto.ProjectGroupConfigRequest;
import com.xiyu.bid.dto.ProjectGroupConfigResponse;
import com.xiyu.bid.admin.service.ProjectGroupService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminProjectGroupControllerTest {

    @Mock
    private ProjectGroupService projectGroupService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminProjectGroupController(projectGroupService))
                .setControllerAdvice(new com.xiyu.bid.exception.GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void getProjectGroups_ShouldReturnPayload() throws Exception {
        when(projectGroupService.getProjectGroups()).thenReturn(ProjectGroupConfigResponse.builder()
                .projectGroups(List.of(ProjectGroupConfigResponse.ProjectGroupItem.builder()
                        .groupCode("G1")
                        .groupName("重点项目组")
                        .manager("项目经理")
                        .memberCount(1)
                        .projectIds(List.of(10L))
                        .build()))
                .userOptions(List.of(ProjectGroupConfigResponse.UserOptionItem.builder()
                        .id(1L)
                        .name("项目经理")
                        .role("manager")
                        .deptCode("BID")
                        .dept("投标管理部")
                        .build()))
                .build());

        mockMvc.perform(get("/api/admin/project-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectGroups[0].groupCode").value("G1"))
                .andExpect(jsonPath("$.data.userOptions[0].name").value("项目经理"));
    }

    @Test
    void createProjectGroup_ShouldReturnSavedPayload() throws Exception {
        ProjectGroupConfigRequest.ProjectGroupItem request = ProjectGroupConfigRequest.ProjectGroupItem.builder()
                .groupCode("G1")
                .groupName("重点项目组")
                .managerUserId(1L)
                .visibility("members")
                .memberUserIds(List.of(2L))
                .projectIds(List.of(10L))
                .build();

        when(projectGroupService.createProjectGroup(any(ProjectGroupConfigRequest.ProjectGroupItem.class)))
                .thenReturn(ProjectGroupConfigResponse.ProjectGroupItem.builder()
                        .id(1L)
                        .groupCode("G1")
                        .groupName("重点项目组")
                        .managerUserId(1L)
                        .memberUserIds(List.of(2L))
                        .projectIds(List.of(10L))
                        .visibility("members")
                        .build());

        mockMvc.perform(post("/api/admin/project-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.groupCode").value("G1"))
                .andExpect(jsonPath("$.data.projectIds[0]").value(10));
    }

    @Test
    void updateProjectGroup_ShouldReturnSavedPayload() throws Exception {
        ProjectGroupConfigRequest.ProjectGroupItem request = ProjectGroupConfigRequest.ProjectGroupItem.builder()
                .groupCode("G1")
                .groupName("重点项目组-更新")
                .managerUserId(1L)
                .visibility("custom")
                .allowedRoles(List.of("staff"))
                .projectIds(List.of(10L))
                .build();

        when(projectGroupService.updateProjectGroup(any(Long.class), any(ProjectGroupConfigRequest.ProjectGroupItem.class)))
                .thenReturn(ProjectGroupConfigResponse.ProjectGroupItem.builder()
                        .id(1L)
                        .groupCode("G1")
                        .groupName("重点项目组-更新")
                        .visibility("custom")
                        .allowedRoles(List.of("staff"))
                        .projectIds(List.of(10L))
                        .build());

        mockMvc.perform(patch("/api/admin/project-groups/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.groupName").value("重点项目组-更新"))
                .andExpect(jsonPath("$.data.allowedRoles[0]").value("staff"));
    }

    @Test
    void deleteProjectGroup_ShouldReturnSuccessAfterEndpointIsIntroduced() throws Exception {
        doNothing().when(projectGroupService).deleteProjectGroup(1L);

        mockMvc.perform(delete("/api/admin/project-groups/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void saveProjectGroups_ShouldRejectUnknownVisibility() throws Exception {
        ProjectGroupConfigRequest request = ProjectGroupConfigRequest.builder()
                .projectGroups(List.of(ProjectGroupConfigRequest.ProjectGroupItem.builder()
                        .groupCode("G1")
                        .groupName("重点项目组")
                        .managerUserId(1L)
                        .visibility("everyone")
                        .memberUserIds(List.of(2L))
                        .projectIds(List.of(10L))
                        .build()))
                .build();

        when(projectGroupService.createProjectGroup(any(ProjectGroupConfigRequest.ProjectGroupItem.class)))
                .thenThrow(new IllegalArgumentException("项目组可见范围非法"));

        mockMvc.perform(post("/api/admin/project-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request.getProjectGroups().get(0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saveProjectGroups_ShouldRejectUnknownAllowedRole() throws Exception {
        ProjectGroupConfigRequest request = ProjectGroupConfigRequest.builder()
                .projectGroups(List.of(ProjectGroupConfigRequest.ProjectGroupItem.builder()
                        .groupCode("G1")
                        .groupName("重点项目组")
                        .managerUserId(1L)
                        .visibility("custom")
                        .allowedRoles(List.of("root"))
                        .projectIds(List.of(10L))
                        .build()))
                .build();

        when(projectGroupService.createProjectGroup(any(ProjectGroupConfigRequest.ProjectGroupItem.class)))
                .thenThrow(new IllegalArgumentException("项目组角色范围非法"));

        mockMvc.perform(post("/api/admin/project-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request.getProjectGroups().get(0))))
                .andExpect(status().isBadRequest());
    }
}
