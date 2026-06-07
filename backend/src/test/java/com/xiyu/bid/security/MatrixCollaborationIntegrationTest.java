package com.xiyu.bid.security;

import com.xiyu.bid.matrixcollaboration.entity.CrmCustomerPermission;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.matrixcollaboration.entity.ProjectMember;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.matrixcollaboration.repository.CrmCustomerPermissionRepository;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MatrixCollaborationIntegrationTest {

    @Autowired
    private ProjectAccessScopeService accessScopeService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private CrmCustomerPermissionRepository crmCustomerPermissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @WithMockUser(username = "collaborator_user")
    void shouldAccessProjectWhenInCollaborationTable() {
        // 1. Create a user
        User user = User.builder()
                .username("collaborator_user")
                .password(passwordEncoder.encode("password"))
                .email("collab@example.com")
                .fullName("Collaborator User")
                .role(User.Role.STAFF)
                .build();
        userRepository.save(user);

        // 2. Create a project in a different department (using 0 for manager_id to avoid accidental match)
        Project project = Project.builder()
                .name("Cross-Dept Project")
                .sourceCustomerId("CUST_001")
                .tenderId(1L)
                .managerId(999L)
                .status(Project.Status.INITIATED)
                .build();
        projectRepository.save(project);

        // 3. Add user to collaboration table
        projectMemberRepository.save(ProjectMember.builder()
                .projectId(project.getId())
                .userId(user.getId())
                .permissionLevel("VIEWER")
                .build());

        // 4. Verify access
        List<Long> allowedIds = accessScopeService.getAllowedProjectIdsForCurrentUser();
        assertTrue(allowedIds.contains(project.getId()), "User should have access to collaborated project");
    }

    @Test
    @WithMockUser(username = "crm_user")
    void shouldAccessProjectWhenAuthorizedInCrm() {
        // 1. Create a user
        User user = User.builder()
                .username("crm_user")
                .password(passwordEncoder.encode("password"))
                .email("crm@example.com")
                .fullName("CRM User")
                .role(User.Role.STAFF)
                .build();
        userRepository.save(user);

        // 2. Create a project for a customer
        Project project = Project.builder()
                .name("CRM Customer Project")
                .sourceCustomerId("CRM_CUST_XYZ")
                .tenderId(2L)
                .managerId(999L)
                .status(Project.Status.INITIATED)
                .build();
        projectRepository.save(project);

        // 3. Add CRM authorization
        crmCustomerPermissionRepository.save(CrmCustomerPermission.builder()
                .customerId("CRM_CUST_XYZ")
                .userId(user.getId())
                .permissionType("TEAM")
                .build());

        // 4. Verify access
        List<Long> allowedIds = accessScopeService.getAllowedProjectIdsForCurrentUser();
        assertTrue(allowedIds.contains(project.getId()), "User should have access to CRM authorized customer's projects");
    }
}
