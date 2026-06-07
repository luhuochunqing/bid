package com.xiyu.bid.compliance.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public final class ComplianceTargetLoader {

    /** 项目仓库. */
    private final ProjectRepository projectRepository;
    /** 标书仓库. */
    private final TenderRepository tenderRepository;

    /**
     * 根据ID获取项目.
     *
     * @param projectId 项目ID
     * @return 项目实体
     */
    public Project requireProject(final Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException(
                        "Project not found with id: " + projectId));
    }

    /**
     * 根据ID获取标书.
     *
     * @param tenderId 标书ID
     * @return 标书实体
     */
    public Tender requireTender(final Long tenderId) {
        return tenderRepository.findById(tenderId)
                .orElseThrow(() -> new RuntimeException(
                        "Tender not found with id: " + tenderId));
    }

    /**
     * 加载项目关联的标书文档内容.
     * 当前简化实现：返回项目文档的文本内容.
     *
     * @param projectId 项目ID
     * @return 文档文本内容
     */
    public String loadProjectDocumentContent(final Long projectId) {
        Project project = requireProject(projectId);
        StringBuilder content = new StringBuilder();
        if (project.getName() != null) {
            content.append("项目名称：").append(project.getName()).append("\n");
        }
        if (project.getDescription() != null) {
            content.append(project.getDescription()).append("\n");
        }
        return content.toString();
    }

    /**
     * 加载项目关联的招标文件文本.
     * 当前简化实现：返回招标文本或项目名称.
     *
     * @param projectId 项目ID
     * @return 招标文本内容
     */
    public String loadProjectTenderText(final Long projectId) {
        Project project = requireProject(projectId);
        if (project.getTenderId() != null) {
            Tender tender = tenderRepository.findById(
                    project.getTenderId()).orElse(null);
            if (tender != null && tender.getDescription() != null) {
                return tender.getDescription();
            }
        }
        return project.getName() != null ? project.getName() : "";
    }
}
