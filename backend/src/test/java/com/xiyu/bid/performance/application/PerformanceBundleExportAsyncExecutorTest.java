package com.xiyu.bid.performance.application;

import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.mapper.PerformanceMapper;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.model.PerformanceRecord;
import com.xiyu.bid.performance.domain.port.PerformanceAlertConfigRepository;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import com.xiyu.bid.performance.infrastructure.PerformanceWordBundleBuilder;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity.ExportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PerformanceBundleExportAsyncExecutor 单元测试。
 *
 * <p>核心防复发目标：
 * <ol>
 *   <li><b>N+1 查询防护</b>：验证 {@code executeExportByIds} 调用 {@link PerformanceRepository#findAllById}
 *       一次，而不是循环 {@link PerformanceRepository#findById} N 次。</li>
 *   <li>状态机流程：markProcessing → complete/fail</li>
 *   <li>Error 处理：catch Error 后标记 FAILED 并重新抛出（参考仓库模块 CO-469 教训）</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class PerformanceBundleExportAsyncExecutorTest {

    @Mock
    private PerformanceBundleExportTaskStateService stateService;

    @Mock
    private PerformanceRepository repository;

    @Mock
    private PerformanceAlertConfigRepository alertConfigRepository;

    @Mock
    private PerformanceMapper mapper;

    @Mock
    private PerformanceWordBundleBuilder wordBundleBuilder;

    @Mock
    private PerformanceBundleExportNotificationPublisher exportPublisher;

    @TempDir
    Path tempDir;

    @InjectMocks
    private PerformanceBundleExportAsyncExecutor asyncExecutor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(asyncExecutor, "exportRoot", tempDir.toString());
    }

    // ========== N+1 查询防护测试（核心防复发） ==========

    /**
     * 防复发测试：验证按 ID 批量导出时调用 findAllById 一次，不调用 findById。
     * <p>如果有人改回循环 findById，本测试会失败：
     * <ul>
     *   <li>verify(repository, times(0)).findById(any()) 会失败</li>
     *   <li>verify(repository, times(1)).findAllById(any()) 会失败</li>
     * </ul>
     */
    @Test
    void executeExportByIds_shouldUseBatchFindAllById_notLoopFindById() throws Exception {
        Long taskId = 1L;
        List<Long> ids = List.of(10L, 20L, 30L);

        PerformanceRecord record = buildRecord(10L);
        PerformanceDTO dto = buildDto(10L);

        when(repository.findAllById(ids)).thenReturn(List.of(record));
        when(mapper.toDTO(record)).thenReturn(dto);
        when(exportPublisher.buildResultSummaryJson(anyInt(), anyLong(), any(), anyLong(), any()))
                .thenReturn("{}");

        PerformanceExportTaskEntity completedTask = PerformanceExportTaskEntity.builder()
                .id(taskId).status(ExportStatus.COMPLETED).createdBy(100L).build();
        when(stateService.complete(any())).thenReturn(completedTask);

        // 执行
        asyncExecutor.executeExportByIds(taskId, ids, Set.of(), 100L,
                "filterSummary", System.currentTimeMillis());

        // 防复发断言 1：必须调用 findAllById 一次
        verify(repository, times(1)).findAllById(eq(ids));
        // 防复发断言 2：禁止调用 findById（N+1 回归会触发）
        verify(repository, times(0)).findById(any());
        // 状态机：markProcessing → complete，不 fail
        verify(stateService, times(1)).markProcessing(taskId);
        verify(stateService, times(1)).complete(any());
        verify(stateService, times(0)).fail(anyLong(), any());
    }

    /**
     * 防复发测试：空 ID 列表也应调用 findAllById 一次（不抛异常）。
     */
    @Test
    void executeExportByIds_emptyIds_shouldCallFindAllByIdOnce() throws Exception {
        Long taskId = 2L;
        List<Long> ids = List.of();

        when(repository.findAllById(ids)).thenReturn(List.of());
        when(exportPublisher.buildResultSummaryJson(anyInt(), anyLong(), any(), anyLong(), any()))
                .thenReturn("{}");

        PerformanceExportTaskEntity completedTask = PerformanceExportTaskEntity.builder()
                .id(taskId).status(ExportStatus.COMPLETED).createdBy(100L).build();
        when(stateService.complete(any())).thenReturn(completedTask);

        asyncExecutor.executeExportByIds(taskId, ids, Set.of(), 100L,
                "filterSummary", System.currentTimeMillis());

        verify(repository, times(1)).findAllById(eq(ids));
        verify(repository, times(0)).findById(any());
        verify(stateService, times(1)).complete(any());
    }

    // ========== 状态机流程测试 ==========

    @Test
    void executeExport_byFilter_shouldMarkProcessingThenComplete() throws Exception {
        Long taskId = 3L;
        PerformanceSearchCriteria criteria = PerformanceSearchCriteria.empty();

        PerformanceRecord record = buildRecord(10L);
        PerformanceDTO dto = buildDto(10L);

        when(alertConfigRepository.findActive()).thenReturn(
                java.util.Optional.of(new PerformanceAlertConfig(null, 180, 90, true)));
        when(repository.findAll(any(), any())).thenReturn(List.of(record));
        when(mapper.toDTO(record)).thenReturn(dto);
        when(exportPublisher.buildResultSummaryJson(anyInt(), anyLong(), any(), anyLong(), any()))
                .thenReturn("{}");

        PerformanceExportTaskEntity completedTask = PerformanceExportTaskEntity.builder()
                .id(taskId).status(ExportStatus.COMPLETED).createdBy(100L).build();
        when(stateService.complete(any())).thenReturn(completedTask);

        asyncExecutor.executeExport(taskId, criteria, Set.of(), 100L,
                "filterSummary", System.currentTimeMillis());

        verify(stateService, times(1)).markProcessing(taskId);
        verify(stateService, times(1)).complete(any());
        verify(stateService, times(0)).fail(anyLong(), any());
    }

    @Test
    void executeExport_whenRepositoryThrows_shouldFailTask() {
        Long taskId = 4L;
        PerformanceSearchCriteria criteria = PerformanceSearchCriteria.empty();

        when(alertConfigRepository.findActive()).thenReturn(
                java.util.Optional.of(new PerformanceAlertConfig(null, 180, 90, true)));
        when(repository.findAll(any(), any())).thenThrow(new RuntimeException("DB 连接失败"));

        asyncExecutor.executeExport(taskId, criteria, Set.of(), 100L,
                "filterSummary", System.currentTimeMillis());

        verify(stateService, times(1)).markProcessing(taskId);
        verify(stateService, times(0)).complete(any());
        verify(stateService, times(1)).fail(eq(taskId), any());
    }

    @Test
    void executeExport_whenErrorThrown_shouldFailTaskAndRethrow() {
        Long taskId = 5L;
        PerformanceSearchCriteria criteria = PerformanceSearchCriteria.empty();

        when(alertConfigRepository.findActive()).thenReturn(
                java.util.Optional.of(new PerformanceAlertConfig(null, 180, 90, true)));
        when(repository.findAll(any(), any())).thenThrow(new OutOfMemoryError("GC overhead"));

        assertThatThrownBy(() ->
                asyncExecutor.executeExport(taskId, criteria, Set.of(), 100L,
                        "filterSummary", System.currentTimeMillis()))
                .isInstanceOf(OutOfMemoryError.class);

        verify(stateService, times(1)).markProcessing(taskId);
        verify(stateService, times(0)).complete(any());
        verify(stateService, times(1)).fail(eq(taskId), any());
    }

    @Test
    void executeExportByIds_whenRepositoryThrows_shouldFailTask() {
        Long taskId = 6L;
        List<Long> ids = List.of(10L, 20L);

        when(repository.findAllById(ids)).thenThrow(new RuntimeException("DB 连接失败"));

        asyncExecutor.executeExportByIds(taskId, ids, Set.of(), 100L,
                "filterSummary", System.currentTimeMillis());

        verify(stateService, times(1)).markProcessing(taskId);
        verify(stateService, times(0)).complete(any());
        verify(stateService, times(1)).fail(eq(taskId), any());
    }

    // ========== 记录数上限防复发测试（P1-2） ==========

    /**
     * 防复发测试：筛选模式导出时，记录数超过 {@link PerformanceBundleExportAsyncExecutor#MAX_EXPORT_RECORDS}
     * 必须标记任务 FAILED，且不调用 wordBundleBuilder.buildBundle（避免 OOM）。
     */
    @Test
    void executeExport_whenRecordsExceedMax_shouldFailTaskWithoutBuildingBundle() {
        Long taskId = 7L;
        PerformanceSearchCriteria criteria = PerformanceSearchCriteria.empty();

        // 构造超过上限的记录列表
        int overLimit = PerformanceBundleExportAsyncExecutor.MAX_EXPORT_RECORDS + 1;
        List<PerformanceRecord> records = java.util.stream.Stream
                .generate(() -> buildRecord(10L))
                .limit(overLimit)
                .toList();

        when(alertConfigRepository.findActive()).thenReturn(
                java.util.Optional.of(new PerformanceAlertConfig(null, 180, 90, true)));
        when(repository.findAll(any(), any())).thenReturn(records);

        asyncExecutor.executeExport(taskId, criteria, Set.of(), 100L,
                "filterSummary", System.currentTimeMillis());

        verify(stateService, times(1)).markProcessing(taskId);
        verify(stateService, times(1)).fail(eq(taskId), any());
        verify(stateService, times(0)).complete(any());
        // 关键：不调用 buildBundle，避免 OOM
        verify(wordBundleBuilder, times(0)).buildBundle(any(), any(), any());
    }

    /**
     * 防复发测试：记录数刚好等于上限时正常导出（边界值）。
     */
    @Test
    void executeExport_whenRecordsAtMax_shouldCompleteNormally() throws Exception {
        Long taskId = 8L;
        PerformanceSearchCriteria criteria = PerformanceSearchCriteria.empty();

        int atLimit = PerformanceBundleExportAsyncExecutor.MAX_EXPORT_RECORDS;
        PerformanceRecord record = buildRecord(10L);
        PerformanceDTO dto = buildDto(10L);
        List<PerformanceRecord> records = java.util.stream.Stream
                .generate(() -> record)
                .limit(atLimit)
                .toList();

        when(alertConfigRepository.findActive()).thenReturn(
                java.util.Optional.of(new PerformanceAlertConfig(null, 180, 90, true)));
        when(repository.findAll(any(), any())).thenReturn(records);
        when(mapper.toDTO(record)).thenReturn(dto);
        when(exportPublisher.buildResultSummaryJson(anyInt(), anyLong(), any(), anyLong(), any()))
                .thenReturn("{}");
        PerformanceExportTaskEntity completedTask = PerformanceExportTaskEntity.builder()
                .id(taskId).status(ExportStatus.COMPLETED).createdBy(100L).build();
        when(stateService.complete(any())).thenReturn(completedTask);

        asyncExecutor.executeExport(taskId, criteria, Set.of(), 100L,
                "filterSummary", System.currentTimeMillis());

        verify(stateService, times(1)).complete(any());
        verify(stateService, times(0)).fail(anyLong(), any());
    }

    // ========== 测试辅助方法 ==========

    private static PerformanceRecord buildRecord(Long id) {
        return new PerformanceRecord(
                id, "合同A", "签约抬头", "集团A", CustomerType.PRIVATE_ENTERPRISE,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                false, null, List.of(), null, null);
    }

    private static PerformanceDTO buildDto(Long id) {
        return new PerformanceDTO(
                id, "合同A", "签约抬头", "集团A", CustomerType.PRIVATE_ENTERPRISE,
                null, null, null, null, null, null, null,
                null, null, null,
                null, null, null, null, null, null, null, false, null, List.of(),
                null, null);
    }
}
