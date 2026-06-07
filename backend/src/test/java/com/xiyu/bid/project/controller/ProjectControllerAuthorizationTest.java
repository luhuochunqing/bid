package com.xiyu.bid.project.controller;

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
    void getAllProjects_shouldAllowRoleCodeBasedProjectRoles() throws Exception {
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
        assertThat(preAuthorize.value())
                .contains("'SALES'")
                .contains("'BID_ADMIN'")
                .contains("'BID_LEAD'")
                .contains("'BID_SPECIALIST'")
                .contains("'TASK_EXECUTOR'")
                .contains("'ADMIN_STAFF'");
    }
}
