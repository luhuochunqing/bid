package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.warehouse.domain.ImportTaskStatus;
import com.xiyu.bid.warehouse.domain.WarehouseImportPolicy;
import com.xiyu.bid.warehouse.domain.WarehouseImportRow;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportExcelReader;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTaskEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WarehouseImportAsyncExecutor 单元测试（从 WarehouseImportAppServiceTest 迁移）。
 *
 * <p>验证从 AppService 提取的 @Async 方法行为：
 * <ol>
 *   <li>新仓库名称导入成功</li>
 *   <li>已存在仓库名称被拒绝且不被更新</li>
 *   <li>混合场景只导入新仓库，已存在行报错</li>
 * </ol>
 *
 * <p>原 WarehouseImportAppServiceTest 测试的是 executeImportAsync 业务逻辑，
 * 该逻辑已迁移到 WarehouseImportAsyncExecutor（提取独立 Bean 修复 @Async self-invocation）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseImportAsyncExecutor 仓库批量导入异步执行器")
class WarehouseImportAsyncExecutorTest {

    @Mock
    private WarehouseImportTaskStateService taskState;
    @Mock
    private WarehouseImportExcelReader excelReader;
    @Mock
    private WarehouseImportRowPersister rowPersister;
    @Mock
    private WarehouseImportAttachmentProcessor attachmentProcessor;
    @Mock
    private WarehouseImportCorrectionFileGenerator correctionFileGenerator;
    @Mock
    private WarehouseNameValidator warehouseNameValidator;

    private WarehouseImportAsyncExecutor executor;

    private static final Long TASK_ID = 1L;
    private static final Long OPERATOR_ID = 100L;

    @BeforeEach
    void setUp() {
        executor = new WarehouseImportAsyncExecutor(
                taskState, excelReader, rowPersister,
                attachmentProcessor, correctionFileGenerator, warehouseNameValidator);

        lenient().when(warehouseNameValidator.loadExistingNames()).thenReturn(java.util.Set.of());
        lenient().when(attachmentProcessor.attachFiles(any(), any(), any(), any()))
                .thenReturn(new WarehouseImportAttachmentProcessor.AttachmentResult(0, List.of()));
    }

    @Test
    @DisplayName("executeImport: 新仓库名称导入成功")
    void newWarehouseName_importedSuccessfully() throws IOException {
        when(excelReader.read(any(byte[].class)))
                .thenReturn(sheetWithRow(validCells("新仓库")));
        when(rowPersister.persist(any(), any())).thenReturn(savedEntity("新仓库"));

        executor.executeImport(TASK_ID, new byte[0], List.of(), operator());

        verify(rowPersister, times(1)).persist(any(), any());
        ArgumentCaptor<Integer> importedCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(taskState).complete(eq(TASK_ID), importedCaptor.capture(), any(), any(), any());
        assertThat(importedCaptor.getValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("executeImport: 已存在仓库名称被拒绝且不被更新")
    void existingWarehouseName_rejectedWithoutUpdate() throws IOException {
        when(excelReader.read(any(byte[].class)))
                .thenReturn(sheetWithRow(validCells("已有仓库")));
        when(warehouseNameValidator.loadExistingNames()).thenReturn(java.util.Set.of("已有仓库"));

        executor.executeImport(TASK_ID, new byte[0], List.of(), operator());

        verify(rowPersister, never()).persist(any(), any());
        ArgumentCaptor<List<WarehouseImportAppService.RowError>> errorsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(taskState).completeWithErrors(eq(TASK_ID), errorsCaptor.capture());
        assertThat(errorsCaptor.getValue())
                .hasSize(1)
                .anyMatch(e -> e.message().contains("已存在") && e.message().contains("已有仓库"));
    }

    @Test
    @DisplayName("executeImport: 混合场景只导入新仓库，已存在行报错")
    void mixedRows_newImportedAndExistingRejected() throws IOException {
        when(excelReader.read(any(byte[].class)))
                .thenReturn(sheetWithRows(validCells("已有仓库"), validCells("新仓库")));
        when(warehouseNameValidator.loadExistingNames()).thenReturn(java.util.Set.of("已有仓库"));
        when(rowPersister.persist(any(), any())).thenAnswer(inv -> {
            WarehouseImportRow r = inv.getArgument(0);
            return savedEntity(r.sanitizedName);
        });

        executor.executeImport(TASK_ID, new byte[0], List.of(), operator());

        verify(rowPersister, times(1)).persist(any(), any());
        ArgumentCaptor<Integer> importedCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<List<WarehouseImportAppService.RowError>> errorsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(taskState).complete(eq(TASK_ID), importedCaptor.capture(), errorsCaptor.capture(), any(), any());
        assertThat(importedCaptor.getValue()).isEqualTo(1);
        assertThat(errorsCaptor.getValue())
                .hasSize(1)
                .anyMatch(e -> e.message().contains("已存在") && e.message().contains("已有仓库"));
    }

    private User operator() {
        return User.builder()
                .id(OPERATOR_ID)
                .username("operator")
                .fullName("操作员")
                .build();
    }

    private WarehouseImportExcelReader.SheetData sheetWithRow(String[] cells) {
        List<String[]> rows = new ArrayList<>();
        rows.add(WarehouseImportPolicy.TEMPLATE_HEADERS);
        rows.add(cells);
        return new WarehouseImportExcelReader.SheetData(rows);
    }

    private WarehouseImportExcelReader.SheetData sheetWithRows(String[]... cells) {
        List<String[]> rows = new ArrayList<>();
        rows.add(WarehouseImportPolicy.TEMPLATE_HEADERS);
        rows.addAll(List.of(cells));
        return new WarehouseImportExcelReader.SheetData(rows);
    }

    private String[] validCells(String name) {
        String[] cells = new String[WarehouseImportPolicy.EXPECTED_COL_COUNT];
        cells[WarehouseImportPolicy.COL_NAME] = name;
        cells[WarehouseImportPolicy.COL_TYPE] = "自营";
        cells[WarehouseImportPolicy.COL_PROVINCE] = "上海市";
        cells[WarehouseImportPolicy.COL_ADDRESS] = "测试地址";
        cells[WarehouseImportPolicy.COL_AREA] = "100";
        cells[WarehouseImportPolicy.COL_REGION] = "华东";
        cells[WarehouseImportPolicy.COL_CONTACT] = "张三";
        cells[WarehouseImportPolicy.COL_START_DATE] = "2026-01-01";
        cells[WarehouseImportPolicy.COL_END_DATE] = "2026-12-31";
        cells[WarehouseImportPolicy.COL_LESSOR] = "出租方";
        cells[WarehouseImportPolicy.COL_LESSEE] = "承租方";
        return cells;
    }

    private WarehouseEntity savedEntity(String name) {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(100L);
        entity.setName(name);
        entity.setStatus(com.xiyu.bid.warehouse.domain.WarehouseStatus.IN_USE);
        return entity;
    }
}
