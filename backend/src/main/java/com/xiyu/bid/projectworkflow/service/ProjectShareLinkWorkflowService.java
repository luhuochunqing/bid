package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.projectworkflow.dto.ProjectShareLinkCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectShareLinkDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectShareLink;
import com.xiyu.bid.projectworkflow.repository.ProjectShareLinkRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class ProjectShareLinkWorkflowService {

    private final ProjectWorkflowGuardService guardService;
    private final ProjectShareLinkRepository projectShareLinkRepository;
    private final UserRepository userRepository;
    private final ProjectShareLinkViewAssembler projectShareLinkViewAssembler;

    List<ProjectShareLinkDTO> getProjectShareLinks(Long projectId) {
        guardService.requireProject(projectId);
        return projectShareLinkRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(projectShareLinkViewAssembler::toDto)
                .toList();
    }

    ProjectShareLinkDTO createProjectShareLink(Long projectId, ProjectShareLinkCreateRequest request) {
        guardService.requireWorkflowMutationProject(projectId);
        String token = UUID.randomUUID().toString().replace("-", "");
        String baseUrl = request.getBaseUrl().trim().replaceAll("/+$", "");
        ProjectShareLink shareLink = ProjectShareLink.builder()
                .projectId(projectId)
                .token(token)
                .url(baseUrl + "/project/" + projectId + "?share=" + token)
                .createdBy(request.getCreatedBy())
                .createdByName(resolveDisplayName(request.getCreatedBy(), request.getCreatedByName()))
                .expiresAt(request.getExpiresAt())
                .build();
        return projectShareLinkViewAssembler.toDto(projectShareLinkRepository.save(shareLink));
    }

    private String resolveDisplayName(Long userId, String fallback) {
        if (userId != null) {
            var user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getFullName() != null && !user.getFullName().isBlank()) {
                return user.getFullName();
            }
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return "未分配";
    }
}
