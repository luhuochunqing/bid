package com.xiyu.bid.security;

import com.xiyu.bid.security.service.ProjectMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectMemberControllerTest {

    private static final String VALID_BODY = """
            {
              "userId": 201,
              "memberRole": "TECHNICAL_EXPERT",
              "permissionLevel": "EDITOR"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectMemberService projectMemberService;

    @Test
    @WithMockUser(roles = "MANAGER")
    void addMember_ShouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/api/projects/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(projectMemberService).addProjectMember(eq(1L), any());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getMembers_ShouldReturnList() throws Exception {
        mockMvc.perform(get("/api/projects/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(projectMemberService).getProjectMembers(1L);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void removeMember_ShouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/api/projects/1/members/201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(projectMemberService).removeProjectMember(1L, 201L);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getMembers_ShouldReturnForbidden_WhenServiceDeniesAccess() throws Exception {
        doThrow(new AccessDeniedException("无权访问该项目"))
                .when(projectMemberService).getProjectMembers(1L);

        mockMvc.perform(get("/api/projects/1/members"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void addMember_ShouldReturnForbidden_WhenServiceDeniesAccess() throws Exception {
        doThrow(new AccessDeniedException("无权访问该项目"))
                .when(projectMemberService).addProjectMember(eq(1L), any());

        mockMvc.perform(post("/api/projects/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void removeMember_ShouldReturnForbidden_WhenServiceDeniesAccess() throws Exception {
        doThrow(new AccessDeniedException("无权访问该项目"))
                .when(projectMemberService).removeProjectMember(1L, 201L);

        mockMvc.perform(delete("/api/projects/1/members/201"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void addMember_ShouldReturn400_WhenUserIdMissing() throws Exception {
        mockMvc.perform(post("/api/projects/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberRole": "TECHNICAL_EXPERT",
                                  "permissionLevel": "EDITOR"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
