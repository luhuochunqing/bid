package com.xiyu.bid.performance.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity.ExportStatus;
import com.xiyu.bid.performance.infrastructure.persistence.repository.PerformanceExportTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * PerformanceBundleExportAppService 单元测试。
 *
 * <p>重点覆盖 {@link PerformanceBundleExportAppService#getExportFile} 的边界场景：
 * <ul>
 *   <li>任务不存在</li>
 *   <li>任务未完成</li>
 *   <li>任务已过期</li>
 *   <li>stored_file_path 为 null</li>
 *   <li>stored_file_path 落在 exportRoot 外（路径遍历防护）</li>
 *   <li>stored_file_path 指向不存在的文件</li>
 *   <li>正常下载</li>
 * </ul>
 *
 * <p>防复发目标：确保 {@code stored_file_path} 白名单校验始终生效，
 * 与附件存储路径防护（PerformanceAttachmentStorageAppService#resolveLocalPath）保持一致。
 */
@ExtendWith(MockitoExtension.class)
class PerformanceBundleExportAppServiceTest {

    @Mock
    private PerformanceExportTaskRepository exportTaskRepo;

    @Mock
    private PerformanceBundleExportAsyncExecutor asyncExecutor;

    @Mock
    private PerformanceBundleExportTaskStateService stateService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PerformanceBundleExportAppService appService;

    @TempDir
    Path tempDir;

    private static final Long TASK_ID = 100L;
    private static final Long OPERATOR_ID = 1000L;

    @BeforeEach
    void setUp() {
        // 将 exportRoot 注入为 @TempDir 绝对路径，确保白名单校验基于真实目录
        ReflectionTestUtils.setField(appService, "exportRoot", tempDir.toString());
    }

    @Test
    @DisplayName("getExportFile: 任务不存在抛 IllegalArgumentException")
    void getExportFile_whenTaskNotExists_throwsIllegalArgument() {
        when(exportTaskRepo.findByIdAndCreatedBy(TASK_ID, OPERATOR_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> appService.getExportFile(TASK_ID, OPERATOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("导出任务不存在");
    }

    @Test
    @DisplayName("getExportFile: 任务未完成抛 IllegalStateException")
    void getExportFile_whenTaskNotCompleted_throwsIllegalState() {
        PerformanceExportTaskEntity task = buildTask(ExportStatus.PROCESSING, null, null);
        when(exportTaskRepo.findByIdAndCreatedBy(TASK_ID, OPERATOR_ID))
                .thenReturn(Optional.of(task));

        assertThatThrownBy(() -> appService.getExportFile(TASK_ID, OPERATOR_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未完成");
    }

    @Test
    @DisplayName("getExportFile: 任务已过期抛 IllegalStateException")
    void getExportFile_whenTaskExpired_throwsIllegalState() {
        PerformanceExportTaskEntity task = buildTask(ExportStatus.COMPLETED,
                tempDir.resolve("export.docx").toString(),
                LocalDateTime.now().minusHours(1));
        when(exportTaskRepo.findByIdAndCreatedBy(TASK_ID, OPERATOR_ID))
                .thenReturn(Optional.of(task));

        assertThatThrownBy(() -> appService.getExportFile(TASK_ID, OPERATOR_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已过期");
    }

    @Test
    @DisplayName("getExportFile: stored_file_path 为 null 抛 IllegalStateException")
    void getExportFile_whenStoredFilePathNull_throwsIllegalState() {
        PerformanceExportTaskEntity task = buildTask(ExportStatus.COMPLETED, null, null);
        when(exportTaskRepo.findByIdAndCreatedBy(TASK_ID, OPERATOR_ID))
                .thenReturn(Optional.of(task));

        assertThatThrownBy(() -> appService.getExportFile(TASK_ID, OPERATOR_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("路径为空");
    }

    @Test
    @DisplayName("getExportFile: stored_file_path 落在 exportRoot 外抛 IllegalStateException（路径遍历防护）")
    void getExportFile_whenPathOutsideExportRoot_throwsIllegalState() throws IOException {
        // 在 @TempDir 之外创建一个文件，模拟 DB 被污染指向系统其他位置
        Path outsideFile = Files.createTempFile("outside-export", ".docx");
        try {
            PerformanceExportTaskEntity task = buildTask(ExportStatus.COMPLETED,
                    outsideFile.toString(), null);
            when(exportTaskRepo.findByIdAndCreatedBy(TASK_ID, OPERATOR_ID))
                    .thenReturn(Optional.of(task));

            assertThatThrownBy(() -> appService.getExportFile(TASK_ID, OPERATOR_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("路径非法");
        } finally {
            Files.deleteIfExists(outsideFile);
        }
    }

    @Test
    @DisplayName("getExportFile: stored_file_path 包含 .. 路径遍历尝试抛 IllegalStateException")
    void getExportFile_whenPathContainsTraversal_throwsIllegalState() {
        // 构造路径遍历尝试：tempDir/../../etc/passwd
        String traversalPath = tempDir.toString() + "/../../etc/passwd";
        PerformanceExportTaskEntity task = buildTask(ExportStatus.COMPLETED,
                traversalPath, null);
        when(exportTaskRepo.findByIdAndCreatedBy(TASK_ID, OPERATOR_ID))
                .thenReturn(Optional.of(task));

        assertThatThrownBy(() -> appService.getExportFile(TASK_ID, OPERATOR_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("路径非法");
    }

    @Test
    @DisplayName("getExportFile: 文件已被清理抛 IllegalStateException")
    void getExportFile_whenFileDeleted_throwsIllegalState() {
        // 指向 exportRoot 子树内但文件不存在
        Path deletedFile = tempDir.resolve("deleted-export.docx");
        PerformanceExportTaskEntity task = buildTask(ExportStatus.COMPLETED,
                deletedFile.toString(), null);
        when(exportTaskRepo.findByIdAndCreatedBy(TASK_ID, OPERATOR_ID))
                .thenReturn(Optional.of(task));

        assertThatThrownBy(() -> appService.getExportFile(TASK_ID, OPERATOR_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已被清理");
    }

    @Test
    @DisplayName("getExportFile: 正常下载返回文件 Path")
    void getExportFile_whenValid_returnsPath() throws IOException {
        // 在 exportRoot 子树内创建真实文件
        Path exportFile = tempDir.resolve("export-20260804.docx");
        Files.createFile(exportFile);

        PerformanceExportTaskEntity task = buildTask(ExportStatus.COMPLETED,
                exportFile.toString(), null);
        when(exportTaskRepo.findByIdAndCreatedBy(TASK_ID, OPERATOR_ID))
                .thenReturn(Optional.of(task));

        Path result = appService.getExportFile(TASK_ID, OPERATOR_ID);
        assertThat(result).exists();
        assertThat(result.normalize()).isEqualTo(exportFile.normalize());
    }

    /**
     * 构造测试任务实体。
     *
     * @param status 任务状态
     * @param storedFilePath 导出文件存储路径
     * @param expiresAt 过期时间（null 表示永不过期）
     */
    private PerformanceExportTaskEntity buildTask(ExportStatus status,
                                                    String storedFilePath,
                                                    LocalDateTime expiresAt) {
        return PerformanceExportTaskEntity.builder()
                .id(TASK_ID)
                .status(status)
                .storedFilePath(storedFilePath)
                .expiresAt(expiresAt)
                .createdBy(OPERATOR_ID)
                .build();
    }
}
