package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.service.ProjectStageService;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDownloadFile;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectDocumentDownloadService} 单元测试，聚焦 OBS 直传分派逻辑。
 * 范式对齐 {@link ProjectDocumentWorkflowServiceTest}：纯 Mockito + 手动 new。
 */
class ProjectDocumentDownloadServiceTest {

    private ProjectDocumentRepository projectDocumentRepository;
    private ProjectDocumentFileStorage fileStorage;
    private ProjectStageService projectStageService;
    private ObsShareUrlSigner obsShareUrlSigner;
    private ProjectDocumentDownloadService downloadService;

    @BeforeEach
    void setUp() {
        projectDocumentRepository = mock(ProjectDocumentRepository.class);
        fileStorage = mock(ProjectDocumentFileStorage.class);
        projectStageService = mock(ProjectStageService.class);
        obsShareUrlSigner = mock(ObsShareUrlSigner.class);

        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectAccessScopeService projectAccessScopeService = mock(ProjectAccessScopeService.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        ProjectScoreDraftRepository projectScoreDraftRepository = mock(ProjectScoreDraftRepository.class);
        ProjectWorkflowGuardService guardService = new ProjectWorkflowGuardService(
                projectRepository, projectAccessScopeService, taskRepository,
                projectDocumentRepository, projectScoreDraftRepository);

        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(
                Project.builder().id(1001L).status(Project.Status.BIDDING).build()));
        // OBS 直传文件（招标文件）默认非 BID，不触发阶段校验
        lenient().when(projectStageService.currentStage(anyLong())).thenReturn(ProjectStage.DRAFTING);

        downloadService = new ProjectDocumentDownloadService(
                guardService, fileStorage, projectStageService, obsShareUrlSigner);
    }

    @Test
    void obsDirectFileShouldReturnRedirectWithSignedUrl() {
        // 正式环境 id=86 场景：fileUrl=obs-direct:{uploadId}
        ProjectDocument doc = ProjectDocument.builder()
                .id(86L)
                .projectId(1001L)
                .name("招标文件.pdf")
                .fileUrl("obs-direct:28387f97-0e0c-4437-80ad-e5a05cafb3aa")
                .documentCategory("TENDER")
                .build();
        when(projectDocumentRepository.findById(86L)).thenReturn(Optional.of(doc));
        when(obsShareUrlSigner.trySign("obs-direct:28387f97-0e0c-4437-80ad-e5a05cafb3aa"))
                .thenReturn(Optional.of("https://obs.example.com/signed-download-url"));

        ProjectDocumentDownloadFile result = downloadService.getProjectDocumentFile(1001L, 86L);

        assertThat(result.redirectUrl()).isEqualTo("https://obs.example.com/signed-download-url");
        assertThat(result.resource()).isNull();
        assertThat(result.fileName()).isEqualTo("招标文件.pdf");
        // OBS 文件不应走 fileStorage.load（本地存储路径）
        verify(fileStorage, never()).load(any());
    }

    @Test
    void localBidAgentFileShouldReturnInlineStream() {
        // 既有本地存储场景不回归
        ProjectDocument doc = ProjectDocument.builder()
                .id(3003L)
                .projectId(1001L)
                .name("任务附件.docx")
                .fileType("docx")
                .fileUrl("bid-agent://tender-documents/1001/file.docx")
                .documentCategory("TASK_ATTACHMENT")
                .build();
        when(projectDocumentRepository.findById(3003L)).thenReturn(Optional.of(doc));
        when(fileStorage.load("bid-agent://tender-documents/1001/file.docx"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "bid-agent://tender-documents/1001/file.docx",
                        null, "application/octet-stream",
                        "内容".getBytes(StandardCharsets.UTF_8))));

        ProjectDocumentDownloadFile result = downloadService.getProjectDocumentFile(1001L, 3003L);

        assertThat(result.redirectUrl()).isNull();
        assertThat(result.resource()).isNotNull();
        assertThat(result.fileName()).isEqualTo("任务附件.docx");
        assertThat(result.contentLength()).isEqualTo("内容".getBytes(StandardCharsets.UTF_8).length);
        // 本地存储不应调 ObsShareUrlSigner
        verify(obsShareUrlSigner, never()).trySign(any());
    }

    @Test
    void obsDirectFileWhenSignerFailsShouldThrow404() {
        // ObsShareUrlSigner 找不到 BidFile（Optional.empty）→ 404
        ProjectDocument doc = ProjectDocument.builder()
                .id(90L)
                .projectId(1001L)
                .name("招标文件.pdf")
                .fileUrl("obs-direct:nonexistent-upload-id")
                .documentCategory("TENDER")
                .build();
        when(projectDocumentRepository.findById(90L)).thenReturn(Optional.of(doc));
        when(obsShareUrlSigner.trySign("obs-direct:nonexistent-upload-id"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> downloadService.getProjectDocumentFile(1001L, 90L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void bidDocumentStageGuardShouldStillWorkForNonDraftingStage() {
        // 不回归：BID 文件在 EVALUATING 阶段仍抛 409（阶段校验不变）
        ProjectDocument doc = ProjectDocument.builder()
                .id(3102L)
                .projectId(1001L)
                .name("投标文件.pdf")
                .fileUrl("obs-direct:upload-id-bid")
                .documentCategory("BID")
                .build();
        when(projectDocumentRepository.findById(3102L)).thenReturn(Optional.of(doc));
        when(projectStageService.currentStage(1001L)).thenReturn(ProjectStage.EVALUATING);

        assertThatThrownBy(() -> downloadService.getProjectDocumentFile(1001L, 3102L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("阶段");
        // 阶段校验在分派前，不应调 signer/fileStorage
        verify(obsShareUrlSigner, never()).trySign(any());
    }

    @Test
    void missingDocumentNameShouldInferExtensionFromFileType() {
        ProjectDocument doc = ProjectDocument.builder()
                .id(3004L)
                .projectId(1001L)
                .name(null)
                .fileType("pdf")
                .fileUrl("bid-agent://tender-documents/1001/file.pdf")
                .documentCategory("TASK_ATTACHMENT")
                .build();
        when(projectDocumentRepository.findById(3004L)).thenReturn(Optional.of(doc));
        when(fileStorage.load("bid-agent://tender-documents/1001/file.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "bid-agent://tender-documents/1001/file.pdf",
                        null, "application/octet-stream",
                        "内容".getBytes(StandardCharsets.UTF_8))));

        ProjectDocumentDownloadFile result = downloadService.getProjectDocumentFile(1001L, 3004L);

        assertThat(result.fileName()).isEqualTo("项目文档.pdf");
    }

    @Test
    void missingDocumentNameWithMimeTypeShouldInferExtensionFromSubtype() {
        // fileType 存储为 MIME 类型时，应从 subtype 推断扩展名，而非主类型（避免 "项目文档.application"）
        ProjectDocument doc = ProjectDocument.builder()
                .id(3004L)
                .projectId(1001L)
                .name(null)
                .fileType("application/pdf")
                .fileUrl("bid-agent://tender-documents/1001/file.pdf")
                .documentCategory("TASK_ATTACHMENT")
                .build();
        when(projectDocumentRepository.findById(3004L)).thenReturn(Optional.of(doc));
        when(fileStorage.load("bid-agent://tender-documents/1001/file.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "bid-agent://tender-documents/1001/file.pdf",
                        null, "application/octet-stream",
                        "内容".getBytes(StandardCharsets.UTF_8))));

        ProjectDocumentDownloadFile result = downloadService.getProjectDocumentFile(1001L, 3004L);

        assertThat(result.fileName()).isEqualTo("项目文档.pdf");
    }

    @Test
    void invalidMimeTypeShouldFallbackToOctetStream() {
        ProjectDocument doc = ProjectDocument.builder()
                .id(3005L)
                .projectId(1001L)
                .name("file.txt")
                .fileType("not/a/valid/mime")
                .fileUrl("bid-agent://tender-documents/1001/file.txt")
                .documentCategory("TASK_ATTACHMENT")
                .build();
        when(projectDocumentRepository.findById(3005L)).thenReturn(Optional.of(doc));
        when(fileStorage.load("bid-agent://tender-documents/1001/file.txt"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "bid-agent://tender-documents/1001/file.txt",
                        null, null,
                        "内容".getBytes(StandardCharsets.UTF_8))));

        ProjectDocumentDownloadFile result = downloadService.getProjectDocumentFile(1001L, 3005L);

        assertThat(result.contentType()).isEqualTo("application/octet-stream");
    }
}
