package com.xiyu.bid.project.integration;

import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class ProjectControllerAccessIntegrationTest extends AbstractProjectControllerIntegrationTest {

    @Test
    @WithMockUser(username = "staff-user", roles = {"MANAGER"})
    void getAllProjects_ShouldFilterProjectsByCurrentMembership() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("真实项目列表回归"));
    }

    @Test
    @WithMockUser(username = "staff-user", roles = {"MANAGER"})
    void createProject_ShouldAllowStaffOwnerWithoutBudget() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "销售创建框架协议项目",
                                  "tenderId": 140,
                                  "status": "INITIATED",
                                  "managerId": %d,
                                  "teamMembers": [%d],
                                  "startDate": "2026-04-01T09:00:00",
                                  "endDate": "2026-05-15T18:00:00",
                                  "customer": "兵工集团MRO商城",
                                  "industry": "电商",
                                  "region": "北京",
                                  "deadline": "2026-05-10",
                                  "description": "框架协议可无明确预算"
                                }
                                """.formatted(staffUser.getId(), staffUser.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("销售创建框架协议项目"))
                .andExpect(jsonPath("$.data.budget").doesNotExist())
                .andExpect(jsonPath("$.data.managerId").value(staffUser.getId()));
    }

    @Test
    @WithMockUser(username = "outsider-user", roles = {"MANAGER"})
    void getProjectById_ShouldReturnForbiddenForUnauthorizedProject() throws Exception {
        mockMvc.perform(get("/api/projects/{id}", visibleProjectId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg").value("权限不足，无法访问该资源"));
    }

    @Test
    @WithMockUser(username = "cross-dept-assignee", roles = {"BID_OTHERDEPT"})
    void getProjectById_ShouldAllowBidOtherDeptAssigneeForAssignedTaskProject() throws Exception {
        mockMvc.perform(get("/api/projects/{id}", visibleProjectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(visibleProjectId))
                .andExpect(jsonPath("$.data.name").value("真实项目列表回归"));
    }

    @Test
    @WithMockUser(username = "cross-dept-assignee", roles = {"BID_OTHERDEPT"})
    void getProjectById_ShouldForbidBidOtherDeptAssigneeForUnassignedProject() throws Exception {
        mockMvc.perform(get("/api/projects/{id}", restrictedProjectId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg").value("权限不足，无法访问该资源"));
    }

    @Test
    @WithMockUser(username = "dept-viewer-user", roles = {"MANAGER"})
    void getAllProjects_ShouldIncludeProjectsGrantedByDepartmentScope() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("真实项目列表回归"));
    }

    @Test
    @WithMockUser(username = "group-viewer-user", roles = {"MANAGER"})
    void getAllProjects_ShouldIncludeProjectsGrantedByProjectGroupRule() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("真实项目列表回归"));
    }

    @Test
    @WithMockUser(username = "group-viewer-user", roles = {"MANAGER"})
    void getAllProjects_ShouldExcludeProjectsAfterProjectGroupIsDeleted() throws Exception {
        projectGroupRepository.deleteAll();

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
