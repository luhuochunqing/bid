// Input: project repository and access scope service
// Output: guarded project loading for project workflow services
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectWorkflowGuard {

    private final ProjectRepository projectRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    public Project requireProject(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", String.valueOf(projectId)));
    }
}
