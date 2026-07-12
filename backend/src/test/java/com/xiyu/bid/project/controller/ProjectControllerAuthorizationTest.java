package com.xiyu.bid.project.controller;

import com.xiyu.bid.project.dto.ClosureApprovalRequest;
import com.xiyu.bid.project.service.ProjectService;
import com.xiyu.bid.project.service.ProjectExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ProjectControllerAuthorizationTest {

    @Mock
    private ProjectService projectService;

    @Mock
    private ProjectExportService projectExportService;

    @Test
    void getAllProjects_shouldBeAccessibleToAuthenticatedUsers() throws Exception {
        // e2282c96 放宽 getAllProjects 的 @PreAuthorize 为 isAuthenticated()（数据权限由 @DataScope 控制）。
        // 该注解比原先的角色白名单更宽松，所有登录用户（含下方角色）均可访问。
        Method method = ProjectController.class.getMethod(
                "getAllProjects",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Long.class,
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Integer.class,
                Integer.class
        );

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("isAuthenticated()");
    }

    @Test
    void approveClosure_shouldAllowAllExpectedRoles() throws Exception {
        Method method = ProjectClosureController.class.getMethod(
                "approve",
                Long.class,
                ClosureApprovalRequest.class,
                org.springframework.security.core.userdetails.UserDetails.class
        );

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value())
                .contains("'ROLE_ADMIN'")
                .contains("'ROLE_BIDADMIN'")
                .contains("'ROLE_BID_TEAMLEADER'")
                .contains("'ROLE_BID_TEAM'")
                .contains("'closure.review'");
    }
}
