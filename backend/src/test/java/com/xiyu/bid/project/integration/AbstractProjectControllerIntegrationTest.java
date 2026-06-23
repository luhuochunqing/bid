package com.xiyu.bid.project.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.ProjectGroup;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.ProjectGroupRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.settings.entity.SystemSetting;
import com.xiyu.bid.settings.repository.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

abstract class AbstractProjectControllerIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ProjectRepository projectRepository;

    @Autowired
    protected ProjectGroupRepository projectGroupRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleProfileRepository roleProfileRepository;

    @Autowired
    protected TaskRepository taskRepository;

    @Autowired
    protected SystemSettingRepository systemSettingRepository;

    @Autowired
    protected ObjectMapper objectMapper;

    protected User managerUser;
    protected User staffUser;
    protected User outsiderUser;
    protected User departmentViewerUser;
    protected User groupViewerUser;
    protected User crossDeptAssigneeUser;
    protected Long visibleProjectId;
    protected Long restrictedProjectId;

    @BeforeEach
    void setUpProjectControllerFixtures() {
        taskRepository.deleteAll();
        projectGroupRepository.deleteAll();
        projectRepository.deleteAll();
        systemSettingRepository.deleteAll();
        userRepository.deleteAll();
        roleProfileRepository.deleteAll();

        RoleProfile defaultProfile = roleProfileRepository.save(
                RoleProfile.builder()
                        .code("test-profile")
                        .name("测试权限")
                        .dataScope("self")
                        .build()
        );
        RoleProfile crossDeptProfile = roleProfileRepository.save(
                RoleProfile.builder()
                        .code(RoleProfileCatalog.BID_OTHER_DEPT_CODE)
                        .name("跨部门协同人员")
                        .dataScope("self")
                        .menuPermissionsValue("task-board,task.view.own,task.handle.own")
                        .build()
        );

        managerUser = createUser(
                "manager-user",
                "manager@example.com",
                "项目经理",
                User.Role.MANAGER,
                "BID",
                "投标管理部",
                defaultProfile
        );
        staffUser = createUser(
                "staff-user",
                "staff@example.com",
                "项目成员",
                User.Role.MANAGER,
                "TECH",
                "技术部",
                defaultProfile
        );
        outsiderUser = createUser(
                "outsider-user",
                "outsider@example.com",
                "外部人员",
                User.Role.MANAGER,
                "SALES",
                "销售部",
                defaultProfile
        );
        departmentViewerUser = createUser(
                "dept-viewer-user",
                "dept-viewer@example.com",
                "同部门查看人",
                User.Role.MANAGER,
                "BID",
                "投标管理部",
                defaultProfile
        );
        groupViewerUser = createUser(
                "group-viewer-user",
                "group-viewer@example.com",
                "项目组查看人",
                User.Role.MANAGER,
                "FINANCE",
                "财务部",
                defaultProfile
        );
        crossDeptAssigneeUser = createUser(
                "cross-dept-assignee",
                "cross-dept-assignee@example.com",
                "跨部门任务执行人",
                User.Role.MANAGER,
                "OTHER",
                "跨部门",
                crossDeptProfile
        );

        Project restrictedProject = projectRepository.save(Project.builder()
                .name("无权限项目")
                .tenderId(102L)
                .status(Project.Status.EVALUATING)
                .managerId(888L)
                .teamMembers(List.of(889L))
                .startDate(LocalDateTime.of(2026, 3, 12, 9, 0))
                .endDate(LocalDateTime.of(2026, 3, 22, 18, 0))
                .build());
        restrictedProjectId = restrictedProject.getId();
        Project visibleProject = projectRepository.save(Project.builder()
                .name("真实项目列表回归")
                .tenderId(101L)
                .status(Project.Status.BIDDING)
                .managerId(managerUser.getId())
                .teamMembers(List.of(staffUser.getId(), 602L))
                .startDate(LocalDateTime.of(2026, 3, 10, 9, 0))
                .endDate(LocalDateTime.of(2026, 3, 20, 18, 0))
                .build());
        visibleProjectId = visibleProject.getId();
        taskRepository.save(Task.builder()
                .projectId(visibleProjectId)
                .title("跨部门协同任务")
                .assigneeId(crossDeptAssigneeUser.getId())
                .status(Task.Status.TODO)
                .priority(Task.Priority.MEDIUM)
                .build());
        projectGroupRepository.saveAndFlush(ProjectGroup.builder()
                .groupCode("G1")
                .groupName("重点项目组")
                .managerUserId(managerUser.getId())
                .visibility(ProjectGroup.Visibility.MEMBERS)
                .memberUserIds(List.of(groupViewerUser.getId()))
                .projectIds(List.of(visibleProjectId))
                .build());

        systemSettingRepository.save(SystemSetting.builder()
                .configKey("data_scope_config")
                .payloadJson(writeDataScopePayload())
                .build());
    }

    private User createUser(
            String username,
            String email,
            String fullName,
            User.Role role,
            String departmentCode,
            String departmentName,
            RoleProfile roleProfile
    ) {
        return userRepository.save(User.builder()
                .username(username)
                .password("encoded")
                .email(email)
                .fullName(fullName)
                .role(role)
                .roleProfile(roleProfile)
                .departmentCode(departmentCode)
                .departmentName(departmentName)
                .enabled(true)
                .build());
    }

    protected String writeDataScopePayload() {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "departmentTree", List.of(
                            java.util.Map.of(
                                    "departmentCode", "BID",
                                    "departmentName", "投标管理部",
                                    "sortOrder", 1
                            ),
                            java.util.Map.of(
                                    "departmentCode", "BID_SUB",
                                    "departmentName", "投标一部",
                                    "parentDepartmentCode", "BID",
                                    "sortOrder", 2
                            ),
                            java.util.Map.of(
                                    "departmentCode", "FINANCE",
                                    "departmentName", "财务部",
                                    "sortOrder", 3
                            )
                    ),
                    "userRules", List.of(),
                    "departmentRules", List.of(
                            java.util.Map.of(
                                    "departmentCode", "BID",
                                    "dataScope", "deptAndSub",
                                    "canViewOtherDepts", false,
                                    "allowedDeptCodes", List.of()
                            )
                    )
            ));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
