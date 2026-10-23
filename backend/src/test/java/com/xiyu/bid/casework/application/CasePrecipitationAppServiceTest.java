package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.casework.infrastructure.ProjectArchiveRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.repository.ProjectResultRepository;
import com.xiyu.bid.project.service.ProjectStageService;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CasePrecipitationAppService 单元测试。
 *
 * <p>聚焦修复核心：评分项前置条件判定数据源从 {@code project_score_drafts} 改为
 * {@code score_item}。覆盖三组分支：
 * <ol>
 *   <li>完整前置条件满足（评分项读 score_item）→ canPrecipitate=true</li>
 *   <li>评分项缺失（score_item 空）→ canPrecipitate=false + 提示含"缺少评分项"</li>
 *   <li>手动触发：前置条件不足抛异常；满足则发布 ProjectClosedEvent</li>
 * </ol>
 */
class CasePrecipitationAppServiceTest {

    private ProjectArchiveRepository archiveRepository;
    private ArchiveFileRepository fileRepository;
    private ScoreItemRepository scoreItemRepository;
    private ProjectRepository projectRepository;
    private ProjectResultRepository projectResultRepository;
    private ApplicationEventPublisher eventPublisher;
    private UserRepository userRepository;
    private CasePrecipitationTaskStateService taskStateService;
    private CasePrecipitationAppService service;

    @BeforeEach
    void setUp() {
        archiveRepository = mock(ProjectArchiveRepository.class);
        fileRepository = mock(ArchiveFileRepository.class);
        scoreItemRepository = mock(ScoreItemRepository.class);
        projectRepository = mock(ProjectRepository.class);
        projectResultRepository = mock(ProjectResultRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        userRepository = mock(UserRepository.class);
        taskStateService = mock(CasePrecipitationTaskStateService.class);
        service = new CasePrecipitationAppService(
                archiveRepository, fileRepository, scoreItemRepository,
                projectRepository, projectResultRepository,
                eventPublisher, userRepository, taskStateService);
    }

    @Test
    @DisplayName("前置条件全满足：项目存在 + 结果已确认 + BID 文件 + score_item 有评分项 → canPrecipitate=true")
    void readiness_metWhenBidFileAndScoreItemPresent() {
        stubProjectResultConfirmedWithBidFileWithScoreItems();

        CasePrecipitationAppService.ReadinessResult result = service.getReadiness(1L);

        assertThat(result.canPrecipitate()).isTrue();
        assertThat(result.hasScoreItems()).isTrue();
        assertThat(result.missingItems()).isEmpty();
    }

    @Test
    @DisplayName("结果未确认：project_result 为空 → canPrecipitate=false + 提示含'需先登记投标结果'")
    void readiness_missingResultConfirmation() {
        stubProjectResultConfirmed();
        when(projectResultRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        stubBidFile();
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L))
                .thenReturn(List.of(scoreItem(101L)));

        CasePrecipitationAppService.ReadinessResult result = service.getReadiness(1L);

        assertThat(result.canPrecipitate()).isFalse();
        assertThat(result.stageClosed()).isFalse();
        assertThat(result.missingItems()).anyMatch(m -> m.contains("需先登记投标结果"));
    }

    @Test
    @DisplayName("评分项判定数据源：score_item 有数据即通过（不再依赖 project_score_drafts）")
    void readiness_hasScoreItemsFromScoreItemDataSource() {
        stubProjectResultConfirmedWithBidFile();

        // 关键：仅当 score_item 返回数据时 hasScoreItems=true
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L))
                .thenReturn(List.of(scoreItem(101L)));

        CasePrecipitationAppService.ReadinessResult result = service.getReadiness(1L);

        assertThat(result.hasScoreItems()).isTrue();
        assertThat(result.canPrecipitate()).isTrue();
        verify(scoreItemRepository, times(1)).findByProjectIdOrderByItemIndexAsc(1L);
    }

    @Test
    @DisplayName("评分项缺失：score_item 为空 → canPrecipitate=false + 提示含'缺少评分项'")
    void readiness_missingScoreItemsWhenScoreItemEmpty() {
        stubProjectResultConfirmedWithBidFile();
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of());

        CasePrecipitationAppService.ReadinessResult result = service.getReadiness(1L);

        assertThat(result.canPrecipitate()).isFalse();
        assertThat(result.hasScoreItems()).isFalse();
        assertThat(result.missingItems()).anyMatch(m -> m.contains("缺少评分项"));
    }

    @Test
    @DisplayName("缺少标书文件 → canPrecipitate=false + 提示含'缺少标书文件'")
    void readiness_missingBidFile() {
        stubProjectResultConfirmed();
        when(archiveRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L))
                .thenReturn(List.of(scoreItem(101L)));

        CasePrecipitationAppService.ReadinessResult result = service.getReadiness(1L);

        assertThat(result.canPrecipitate()).isFalse();
        assertThat(result.hasBidFile()).isFalse();
        assertThat(result.missingItems()).anyMatch(m -> m.contains("缺少标书文件"));
    }

    @Test
    @DisplayName("手动触发：前置条件不足 → 抛 IllegalArgumentException，不发事件")
    void triggerPrecipitation_rejectsWhenNotReady() {
        // 缺评分项
        stubProjectResultConfirmedWithBidFile();
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.triggerPrecipitation(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("前置条件不满足")
                .hasMessageContaining("缺少评分项");
        verify(eventPublisher, never()).publishEvent(any(ProjectClosedEvent.class));
    }

    @Test
    @DisplayName("手动触发：前置条件满足 → 发布 ProjectClosedEvent")
    void triggerPrecipitation_publishesEventWhenReady() {
        stubProjectResultConfirmedWithBidFileWithScoreItems();
        var task = new CasePrecipitationTaskStateService.TaskView("task-1", 1L, "PENDING", "等待执行",
                null, null, null, null, null);
        when(taskStateService.claim(1L, null))
                .thenReturn(new CasePrecipitationTaskStateService.ClaimResult(task, false));

        service.triggerPrecipitation(1L);

        verify(eventPublisher, times(1)).publishEvent(any(ProjectClosedEvent.class));
    }

    private void stubProjectResultConfirmed() {
        Project project = new Project();
        project.setId(1L);
        project.setName("测试项目");
        project.setStage(ProjectStage.RESULT_PENDING.name());
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        com.xiyu.bid.project.entity.ProjectResult result = new com.xiyu.bid.project.entity.ProjectResult();
        result.setId(88L);
        result.setProjectId(1L);
        result.setResultType("WON");
        when(projectResultRepository.findByProjectId(1L)).thenReturn(Optional.of(result));
    }

    private void stubBidFile() {
        ProjectArchive archive = new ProjectArchive();
        archive.setId(99L);
        when(archiveRepository.findByProjectId(1L)).thenReturn(Optional.of(archive));
        when(fileRepository.findByArchiveId(99L)).thenReturn(List.of(bidFile(1L, 99L)));
    }

    private void stubProjectResultConfirmedWithBidFile() {
        stubProjectResultConfirmed();
        stubBidFile();
    }

    private void stubProjectResultConfirmedWithBidFileWithScoreItems() {
        stubProjectResultConfirmedWithBidFile();
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L))
                .thenReturn(List.of(scoreItem(101L)));
    }

    private ScoreItem scoreItem(Long id) {
        return ScoreItem.builder().id(id).dim("技术方案").detail("要求提供架构图").build();
    }

    private ArchiveFile bidFile(Long id, Long archiveId) {
        ArchiveFile f = new ArchiveFile();
        f.setId(id);
        f.setArchiveId(archiveId);
        f.setFileName("bid.pdf");
        f.setDocumentCategory("BID");
        f.setFilePath("/tmp/placeholder-bid.pdf");
        f.setFileSize(1024L);
        f.setUploadUserId(7L);
        f.setUploadUserName("uploader");
        return f;
    }
}
