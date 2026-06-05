// Input: project access service and project-linked entity ids
// Output: item-level access decision for batch operations
// Pos: Service/权限支撑层
// 维护声明: 仅维护批量操作的项目数据权限判断；业务状态流转请留在对应命令服务。
package com.xiyu.bid.batch.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class BatchProjectAccessGuard {

    private final ProjectAccessScopeService projectAccessScopeService;
    private final ProjectRepository projectRepository;

    void requireProject(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
    }

    void requireTender(Long tenderId) {
        List<Project> linkedProjects = projectRepository.findByTenderId(tenderId);
        for (Project project : linkedProjects) {
            requireProject(project.getId());
        }
    }

    static boolean isAccessDenied(RuntimeException exception) {
        return exception instanceof AccessDeniedException;
    }
}
