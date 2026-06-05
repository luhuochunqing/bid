package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.casework.infrastructure.ProjectArchiveRepository;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.entity.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
/**
 * 案例沉淀前置条件校验与手动触发服务。
 * <p>前置条件：标书文件存在 + 评分项存在。</p>
 */
public class CasePrecipitationAppService {

    private final ProjectArchiveRepository archiveRepository;
    private final ArchiveFileRepository fileRepository;
    private final ProjectScoreDraftRepository scoreDraftRepository;
    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    /**
     * 检查项目是否满足案例沉淀前置条件。
     * @param projectId 项目ID
     * @return 前置条件检查结果，含缺失项说明
     */
    public ReadinessResult getReadiness(Long projectId) {
        List<String> missingItems = new ArrayList<>();
        boolean hasBidFile = false;
        boolean hasScoreItems = false;

        Optional<ProjectArchive> archiveOpt = archiveRepository.findByProjectId(projectId);
        if (archiveOpt.isPresent()) {
            List<ArchiveFile> files = fileRepository.findByArchiveId(archiveOpt.get().getId());
            hasBidFile = files.stream().anyMatch(f -> "BID".equals(f.getDocumentCategory()));
        }

        if (!hasBidFile) {
            missingItems.add("缺少标书文件，请先在标书编制阶段上传标书文件");
        }

        List<ProjectScoreDraft> drafts = scoreDraftRepository
                .findByProjectIdOrderByCategoryAscSourceTableIndexAscSourceRowIndexAsc(projectId);
        hasScoreItems = !drafts.isEmpty();

        if (!hasScoreItems) {
            missingItems.add("缺少评分项，请先在标书编制阶段完成招标文件解析");
        }

        boolean canPrecipitate = hasBidFile && hasScoreItems;
        return new ReadinessResult(canPrecipitate, hasBidFile, hasScoreItems, missingItems);
    }

    /**
     * 手动触发案例沉淀，发布 ProjectClosedEvent 由监听器异步处理。
     * @param projectId 项目ID
     * @throws IllegalArgumentException 项目不存在时抛出
     */
    public void triggerPrecipitation(Long projectId) {
        Optional<Project> projOpt = projectRepository.findById(projectId);
        if (projOpt.isEmpty()) {
            throw new IllegalArgumentException("项目不存在: " + projectId);
        }
        Project project = projOpt.get();
        log.info("Manual case precipitation triggered for project: {} ({})", projectId, project.getName());
        eventPublisher.publishEvent(new ProjectClosedEvent(this, projectId, project.getName()));
    }

    /**
     * 案例沉淀前置条件检查结果。
     * @param canPrecipitate 是否满足全部前置条件
     * @param hasBidFile     是否有标书文件
     * @param hasScoreItems  是否有评分项
     * @param missingItems   缺失项的中文提示列表
     */
    public record ReadinessResult(
            boolean canPrecipitate,
            boolean hasBidFile,
            boolean hasScoreItems,
            List<String> missingItems) {
    }
}
