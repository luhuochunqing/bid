package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.TenderDuplicateException;
import com.xiyu.bid.tender.crm.CachedCrmLookupService;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.dto.TenderImportProgressDTO;
import com.xiyu.bid.tender.dto.TenderImportResultDTO;
import com.xiyu.bid.tender.dto.TenderImportTaskDTO;
import com.xiyu.bid.tender.dto.TenderImportTaskError;
import com.xiyu.bid.tender.dto.TenderRequest;
import com.xiyu.bid.tender.entity.TenderImportTask;
import com.xiyu.bid.tender.repository.TenderImportTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 标讯导入应用服务单元测试（{@link TenderImportAppService}）。
 *
 * <p>覆盖契约：
 * <ul>
 *   <li>triggerImport：校验文件 → 创建 PENDING → 读 byte[] → 触发 self.executeImportAsync</li>
 *   <li>executeImportAsync 成功路径：markProcessing → 逐条 createTender → markCompleted</li>
 *   <li>executeImportAsync 部分成功路径：成功 + 重复 → markPartialSuccess</li>
 *   <li>executeImportAsync 全失败路径：所有行重复 → markFailed</li>
 *   <li>executeImportAsync 校验未通过：parseExcel 有 errors → markFailed（不进入循环）</li>
 *   <li>executeImportAsync 系统异常：catch RuntimeException → failTaskWithThreeLayerFallback</li>
 *   <li>executeImportAsync Error 异常：catch Error → failTaskWithThreeLayerFallback（CO-469 第四轮教训）</li>
 *   <li>getProgress 校验任务归属：userId 不匹配 → AccessDeniedException</li>
 *   <li>getProgress 任务不存在 → ResourceNotFoundException</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TenderImportAppServiceTest {

    @Mock
    private TenderImportService tenderImportService;
    @Mock
    private TenderImportTaskStateService taskStateService;
    @Mock
    private TenderImportProgressService progressService;
    @Mock
    private TenderImportTaskRepository taskRepository;
    @Mock
    private TenderCommandService tenderCommandService;
    @Mock
    private TenderMapper tenderMapper;
    @Mock
    private TenderExcelParser excelParser;
    @Mock
    private CachedCrmLookupService cachedCrmLookupService;

    private TenderImportAppService service;

    @BeforeEach
    void setUp() {
        service = new TenderImportAppService(
                tenderImportService, taskStateService, progressService,
                taskRepository, tenderCommandService, tenderMapper, excelParser,
                cachedCrmLookupService);
        // 手动注入 self 代理（@Lazy @Autowired 字段）
        // 单元测试中 self 就是 service 本身，但 triggerImport 通过 self 调用 executeImportAsync
        // 这里我们 mock self 为 service 本身（不需要拦截，因为 executeImportAsync 不带 @Async 也能直接调用）
        org.springframework.test.util.ReflectionTestUtils.setField(service, "self", service);
    }

    // ==================== triggerImport ====================

    @Test
    @DisplayName("triggerImport: 校验文件 + 创建 PENDING + 触发 executeImportAsync + 返回 TaskDTO")
    void triggerImport_success_createsTaskAndTriggersAsync() {
        Long userId = 100L;
        MultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3});

        // 注意：self 是 service 本身，executeImportAsync 会被实际调用
        // 为避免真实执行异步逻辑，用 doNothing 风格 mock 依赖
        // 但因 self 是真实对象，不能直接 mock。改为 spy + suppress。
        // 简化方案：让 executeImportAsync 的所有依赖返回安全值，使其快速完成
        when(excelParser.parseExcel(any())).thenReturn(
                new TenderExcelParser.ParsedExcel(List.of(), List.of(), 0));

        TenderImportTaskDTO result = service.triggerImport(file, userId);

        assertThat(result).isNotNull();
        assertThat(result.taskId()).isNotBlank();
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.message()).contains("导入任务已创建");
        verify(tenderImportService, times(1)).validateFile(file);
        verify(taskStateService, times(1)).createTask(eq(result.taskId()), eq(userId), eq("test.xlsx"));
        // executeImportAsync 已被触发（因 self=service 本身）
        verify(taskStateService, times(1)).markProcessing(result.taskId());
    }

    @Test
    @DisplayName("triggerImport: 文件读取失败 → failTaskWithThreeLayerFallback + 抛 IllegalArgumentException")
    void triggerImport_fileReadFails_failsTaskAndThrows() {
        Long userId = 100L;
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("bad.xlsx");
        try {
            when(file.getBytes()).thenThrow(new java.io.IOException("disk error"));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }

        assertThatThrownBy(() -> service.triggerImport(file, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("读取文件失败");

        verify(taskStateService, times(1)).failTaskWithThreeLayerFallback(anyString(), anyList());
    }

    // ==================== executeImportAsync 成功路径 ====================

    @Test
    @DisplayName("executeImportAsync: 全部成功 → markCompleted + finalizeProgress")
    void executeImportAsync_allSuccess_markCompleted() {
        String taskId = "task-all-success";
        Long userId = 100L;
        byte[] fileBytes = new byte[]{1, 2, 3};

        TenderRequest req1 = new TenderRequest();
        req1.setTitle("标讯A");
        TenderRequest req2 = new TenderRequest();
        req2.setTitle("标讯B");
        List<TenderRequest> rows = List.of(req1, req2);

        when(excelParser.parseExcel(fileBytes)).thenReturn(
                new TenderExcelParser.ParsedExcel(rows, List.of(), 2));
        when(tenderMapper.toDTO(any(TenderRequest.class))).thenReturn(new TenderDTO());

        service.executeImportAsync(taskId, fileBytes, userId);

        verify(taskStateService, times(1)).markProcessing(taskId);
        // 031 R-007：导入循环外包 openBatch/closeBatch
        verify(cachedCrmLookupService, times(1)).openBatch();
        verify(cachedCrmLookupService, times(1)).closeBatch();
        verify(tenderCommandService, times(2)).createTender(any(TenderDTO.class), eq(userId));
        verify(taskStateService, times(1)).markCompleted(taskId, 2);
        verify(taskStateService, never()).markFailed(eq(taskId), anyList());
        verify(taskStateService, never()).markPartialSuccess(eq(taskId), anyInt(), anyInt(), anyInt(), anyList());
        verify(progressService, times(1)).clearProgress(taskId);
    }

    @Test
    @DisplayName("executeImportAsync: 部分成功（1 成功 + 1 重复）→ markPartialSuccess")
    void executeImportAsync_partialSuccess_markPartialSuccess() {
        String taskId = "task-partial";
        Long userId = 100L;
        byte[] fileBytes = new byte[]{1, 2, 3};

        TenderRequest req1 = new TenderRequest();
        req1.setTitle("标讯A");
        TenderRequest req2 = new TenderRequest();
        req2.setTitle("标讯B");
        req2.setPurchaserName("采购方B");
        List<TenderRequest> rows = List.of(req1, req2);

        // 模拟重复异常（TenderDuplicateException 接受 List<Tender>）
        Tender existingTender = Tender.builder().id(999L).title("已存在的标讯B").build();
        TenderDuplicateException dupEx = new TenderDuplicateException(List.of(existingTender));

        when(excelParser.parseExcel(fileBytes)).thenReturn(
                new TenderExcelParser.ParsedExcel(rows, List.of(), 2));
        when(tenderMapper.toDTO(any(TenderRequest.class))).thenReturn(new TenderDTO());
        when(tenderCommandService.createTender(any(TenderDTO.class), eq(userId)))
                .thenReturn(new TenderDTO())            // 第一次成功
                .thenThrow(dupEx);                       // 第二次重复

        service.executeImportAsync(taskId, fileBytes, userId);

        verify(taskStateService, times(1)).markPartialSuccess(eq(taskId), eq(2), eq(1), eq(1), anyList());
        verify(taskStateService, never()).markCompleted(eq(taskId), anyInt());
        verify(taskStateService, never()).markFailed(eq(taskId), anyList());
    }

    @Test
    @DisplayName("executeImportAsync: 全失败（全部重复）→ markFailed")
    void executeImportAsync_allFailed_markFailed() {
        String taskId = "task-all-failed";
        Long userId = 100L;
        byte[] fileBytes = new byte[]{1, 2, 3};

        TenderRequest req1 = new TenderRequest();
        req1.setTitle("标讯A");
        req1.setPurchaserName("采购方A");
        List<TenderRequest> rows = List.of(req1);

        TenderDuplicateException dupEx = new TenderDuplicateException(
                List.of(Tender.builder().id(998L).title("已存在").build()));

        when(excelParser.parseExcel(fileBytes)).thenReturn(
                new TenderExcelParser.ParsedExcel(rows, List.of(), 1));
        when(tenderMapper.toDTO(any(TenderRequest.class))).thenReturn(new TenderDTO());
        when(tenderCommandService.createTender(any(TenderDTO.class), eq(userId)))
                .thenThrow(dupEx);

        service.executeImportAsync(taskId, fileBytes, userId);

        verify(taskStateService, times(1)).markFailed(eq(taskId), anyList());
        verify(taskStateService, never()).markCompleted(eq(taskId), anyInt());
        verify(taskStateService, never()).markPartialSuccess(eq(taskId), anyInt(), anyInt(), anyInt(), anyList());
    }

    @Test
    @DisplayName("executeImportAsync: Excel 校验未通过（parseExcel 返回 errors）→ markFailed，不进入循环")
    void executeImportAsync_validationFails_markFailedNoLoop() {
        String taskId = "task-validation-fail";
        Long userId = 100L;
        byte[] fileBytes = new byte[]{1, 2, 3};

        List<TenderImportResultDTO.RowError> parseErrors = List.of(
                new TenderImportResultDTO.RowError(2, "title", "标题不能为空"));
        when(excelParser.parseExcel(fileBytes)).thenReturn(
                new TenderExcelParser.ParsedExcel(List.of(), parseErrors, 1));

        service.executeImportAsync(taskId, fileBytes, userId);

        verify(taskStateService, times(1)).markFailed(eq(taskId), anyList());
        verify(tenderCommandService, never()).createTender(any(), any());
        verify(taskStateService, never()).markCompleted(eq(taskId), anyInt());
    }

    // ==================== executeImportAsync 异常兜底 ====================

    @Test
    @DisplayName("executeImportAsync: parseExcel 抛 RuntimeException → failTaskWithThreeLayerFallback")
    void executeImportAsync_runtimeException_failTaskWithThreeLayerFallback() {
        String taskId = "task-runtime-ex";
        Long userId = 100L;
        byte[] fileBytes = new byte[]{1, 2, 3};

        when(excelParser.parseExcel(fileBytes))
                .thenThrow(new RuntimeException("模拟解析异常"));

        service.executeImportAsync(taskId, fileBytes, userId);

        verify(taskStateService, times(1)).failTaskWithThreeLayerFallback(eq(taskId), anyList());
        verify(taskStateService, never()).markCompleted(eq(taskId), anyInt());
        verify(taskStateService, never()).markPartialSuccess(eq(taskId), anyInt(), anyInt(), anyInt(), anyList());
    }

    @Test
    @DisplayName("executeImportAsync: parseExcel 抛 Error → failTaskWithThreeLayerFallback（CO-469 第四轮教训）")
    void executeImportAsync_error_failTaskWithThreeLayerFallback() {
        String taskId = "task-error";
        Long userId = 100L;
        byte[] fileBytes = new byte[]{1, 2, 3};

        when(excelParser.parseExcel(fileBytes))
                .thenThrow(new OutOfMemoryError("模拟 OOM"));

        service.executeImportAsync(taskId, fileBytes, userId);

        verify(taskStateService, times(1)).failTaskWithThreeLayerFallback(eq(taskId), anyList());
    }

    // ==================== getProgress ====================

    @Test
    @DisplayName("getProgress: 任务存在且归属正确 → 返回进度")
    void getProgress_taskExistsAndOwned_returnsProgress() {
        String taskId = "task-progress";
        Long userId = 100L;

        TenderImportTask task = TenderImportTask.builder()
                .taskId(taskId).userId(userId).build();
        when(taskRepository.findByTaskId(taskId)).thenReturn(Optional.of(task));

        TenderImportProgressDTO expected = new TenderImportProgressDTO(
                taskId, "PROCESSING", 100, 50, 45, 5, 50, null, null, null);
        when(progressService.getProgress(taskId)).thenReturn(expected);

        TenderImportProgressDTO result = service.getProgress(taskId, userId);

        assertThat(result).isEqualTo(expected);
        verify(progressService, times(1)).getProgress(taskId);
    }

    @Test
    @DisplayName("getProgress: 任务不存在 → ResourceNotFoundException")
    void getProgress_taskNotFound_throwsResourceNotFound() {
        String taskId = "task-not-exist";
        Long userId = 100L;
        when(taskRepository.findByTaskId(taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProgress(taskId, userId))
                .isInstanceOf(com.xiyu.bid.exception.ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getProgress: 任务归属不匹配 → AccessDeniedException")
    void getProgress_taskNotOwned_throwsAccessDenied() {
        String taskId = "task-other-user";
        Long currentUserId = 100L;
        Long taskOwnerId = 200L;

        TenderImportTask task = TenderImportTask.builder()
                .taskId(taskId).userId(taskOwnerId).build();
        when(taskRepository.findByTaskId(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.getProgress(taskId, currentUserId))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
