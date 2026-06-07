package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.casework.infrastructure.ProjectArchiveRepository;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.service.ProjectStageService;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
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
 * <p>前置条件（蓝图 4.1.1.2.1）：
 * <ol>
 *   <li>项目存在</li>
 *   <li>项目阶段已进入 CLOSED</li>
 *   <li>项目下至少有一份 BID 分类文件（标书文件）</li>
 *   <li>项目下至少有一项评分项</li>
 * </ol>
 * </p>
 */
public class CasePrecipitationAppService {

    private final ProjectArchiveRepository archiveRepository;
    private final ArchiveFileRepository fileRepository;
    private final ProjectScoreDraftRepository scoreDraftRepository;
    private final ProjectRepository projectRepository;
    private final ProjectStageService projectStageService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    /**
     * 检查项目是否满足案例沉淀前置条件。
     * @param projectId 项目ID
     * @return 前置条件检查结果，含缺失项说明
     */
    public ReadinessResult getReadiness(Long projectId) {
        List<String> missingItems = new ArrayList<>();
        boolean projectExists = false;
        boolean stageClosed = false;
        boolean hasBidFile = false;
        boolean hasScoreItems = false;

        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) {
            missingItems.add("项目不存在");
            return new ReadinessResult(false, false, false, false, false, missingItems);
        }
        projectExists = true;

        ProjectStage currentStage = projectStageService.currentStage(projectId);
        stageClosed = currentStage == ProjectStage.CLOSED;
        if (!stageClosed) {
            missingItems.add("项目阶段未进入 CLOSED（当前阶段：" + currentStage + "），需先完成复盘并结项");
        }

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

        boolean canPrecipitate = projectExists && stageClosed && hasBidFile && hasScoreItems;
        return new ReadinessResult(canPrecipitate, stageClosed, hasBidFile, hasScoreItems, false, missingItems);
    }

    /**
     * 手动触发案例沉淀，前置条件不满足直接抛异常而非静默跳过。
     * @param projectId 项目ID
     * @throws IllegalArgumentException 项目不存在或前置条件不满足
     */
    public void triggerPrecipitation(Long projectId) {
        ReadinessResult readiness = getReadiness(projectId);
        if (!readiness.canPrecipitate()) {
            String reason = "案例沉淀前置条件不满足："
                    + String.join("；", readiness.missingItems());
            log.warn("Reject manual precipitation for project {}: {}", projectId, reason);
            throw new IllegalArgumentException(reason);
        }
        Project project = projectRepository.findById(projectId).orElseThrow();
        // 蓝图 4.1.2：手动触发时把当前用户塞进事件，让异步完成通知送达"任务发起人"。
        Long triggerUserId = resolveCurrentUserId();
        log.info("Manual case precipitation triggered for project: {} ({}) by user {}",
                projectId, project.getName(), triggerUserId);
        eventPublisher.publishEvent(new ProjectClosedEvent(this, projectId, project.getName(), triggerUserId));
    }

    private Long resolveCurrentUserId() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return null;
        }
        return userRepository.findByUsername(auth.getName())
                .map(User::getId)
                .orElse(null);
    }

    /**
     * 案例沉淀前置条件检查结果。
     * @param canPrecipitate 是否满足全部前置条件
     * @param stageClosed    项目阶段是否已进入 CLOSED
     * @param hasBidFile     是否有标书文件
     * @param hasScoreItems  是否有评分项
     * @param rejected       是否曾因前置条件不足被拒绝（保留字段）
     * @param missingItems   缺失项的中文提示列表
     */
    public record ReadinessResult(
            boolean canPrecipitate,
            boolean stageClosed,
            boolean hasBidFile,
            boolean hasScoreItems,
            boolean rejected,
            List<String> missingItems) {
    }
}
