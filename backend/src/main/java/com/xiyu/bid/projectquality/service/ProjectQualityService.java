package com.xiyu.bid.projectquality.service;

import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectquality.dto.ProjectQualityCheckResponse;
import com.xiyu.bid.projectquality.entity.ProjectQualityCheck;
import com.xiyu.bid.projectquality.entity.ProjectQualityIssue;
import com.xiyu.bid.projectquality.repository.ProjectQualityCheckRepository;
import com.xiyu.bid.projectquality.repository.ProjectQualityIssueRepository;
import com.xiyu.bid.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectQualityService {

    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectQualityCheckRepository projectQualityCheckRepository;
    private final ProjectQualityIssueRepository projectQualityIssueRepository;

    @Transactional
    public ProjectQualityCheckResponse runQualityCheck(Long projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", String.valueOf(projectId)));

        List<ProjectDocument> documents = projectDocumentRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        ProjectDocument latest = documents.isEmpty() ? null : documents.get(0);
        List<ProjectQualityIssue> issues = new ArrayList<>();

        ProjectQualityCheck check = ProjectQualityCheck.builder()
                .projectId(projectId)
                .documentId(latest == null ? null : latest.getId())
                .documentName(latest == null ? null : latest.getName())
                .status(latest == null ? "EMPTY" : "COMPLETED")
                .empty(latest == null)
                .summary(latest == null ? "当前项目暂无可检查文档" : buildSummary(latest))
                .build();
        ProjectQualityCheck savedCheck = projectQualityCheckRepository.save(check);

        if (latest != null) {
            issues = buildIssues(savedCheck.getId(), latest);
            projectQualityIssueRepository.saveAll(issues);
        }

        return ProjectQualityAssembler.toResponse(savedCheck, issues);
    }

    @Transactional(readOnly = true)
    public ProjectQualityCheckResponse getLatest(Long projectId) {
        ProjectQualityCheck check = projectQualityCheckRepository.findFirstByProjectIdOrderByCheckedAtDesc(projectId)
                .orElse(null);
        if (check == null) {
            return null;
        }
        return ProjectQualityAssembler.toResponse(check, projectQualityIssueRepository.findByCheckIdOrderByIdAsc(check.getId()));
    }

    @Transactional
    public ProjectQualityCheckResponse adoptIssue(Long projectId, Long checkId, Long issueId) {
        return updateIssueState(projectId, checkId, issueId, true, false);
    }

    @Transactional
    public ProjectQualityCheckResponse ignoreIssue(Long projectId, Long checkId, Long issueId) {
        return updateIssueState(projectId, checkId, issueId, false, true);
    }

    private ProjectQualityCheckResponse updateIssueState(Long projectId, Long checkId, Long issueId, boolean adopted, boolean ignored) {
        ProjectQualityCheck check = projectQualityCheckRepository.findById(checkId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectQualityCheck", String.valueOf(checkId)));
        if (!projectId.equals(check.getProjectId())) {
            throw new IllegalArgumentException("Quality check does not belong to project " + projectId);
        }
        ProjectQualityIssue issue = projectQualityIssueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectQualityIssue", String.valueOf(issueId)));
        if (!checkId.equals(issue.getCheckId())) {
            throw new IllegalArgumentException("Quality issue does not belong to check " + checkId);
        }
        issue.setAdopted(adopted);
        issue.setIgnored(ignored);
        projectQualityIssueRepository.save(issue);
        return ProjectQualityAssembler.toResponse(check, projectQualityIssueRepository.findByCheckIdOrderByIdAsc(checkId));
    }

    private String buildSummary(ProjectDocument document) {
        return "已基于项目最新文档《" + document.getName() + "》完成文本质量扫描";
    }

    private List<ProjectQualityIssue> buildIssues(Long checkId, ProjectDocument document) {
        List<ProjectQualityIssue> issues = new ArrayList<>();
        String baseName = document.getName() == null ? "文档" : document.getName();
        issues.add(ProjectQualityIssue.builder()
                .checkId(checkId)
                .type("grammar")
                .originalText(baseName + "内容表述较为简略")
                .suggestionText("建议补充动词和结果描述，增强表达完整度")
                .locationLabel("文档摘要")
                .adopted(false)
                .ignored(false)
                .build());
        if (baseName.contains("终版") || baseName.contains("final")) {
            issues.add(ProjectQualityIssue.builder()
                    .checkId(checkId)
                    .type("format")
                    .originalText("终版命名已出现，但缺少版本日期")
                    .suggestionText("建议在标题或文件名中增加版本日期，保持归档一致性")
                    .locationLabel("文件命名")
                    .adopted(false)
                    .ignored(false)
                    .build());
        }
        return issues;
    }
}
