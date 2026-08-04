package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.common.util.StringUtils;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.warehouse.domain.ImportTaskStatus;
import com.xiyu.bid.warehouse.domain.WarehouseImportPolicy;
import com.xiyu.bid.warehouse.domain.WarehouseImportRow;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportExcelReader;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 仓库批量导入异步执行器 — 承载 @Async 方法，从 WarehouseImportAppService 提取。
 *
 * <p>修复与 {@link WarehouseExportAsyncExecutor} 同源的 self-invocation 问题：
 * 原 WarehouseImportAppService.triggerImport() 在 @Transactional 内直接调用
 * this.executeImportAsync()，@Async 注解失效。
 *
 * <p>状态机委托 {@link WarehouseImportTaskStateService}，与 Export 模块模式对齐。
 *
 * <p>catch Error 实践参考 CO-469 第四轮教训，防止 OOM 等导致任务卡死。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WarehouseImportAsyncExecutor {

    private final WarehouseImportTaskStateService taskState;
    private final WarehouseImportExcelReader excelReader;
    private final WarehouseImportRowPersister rowPersister;
    private final WarehouseImportAttachmentProcessor attachmentProcessor;
    private final WarehouseImportCorrectionFileGenerator correctionFileGenerator;
    private final WarehouseNameValidator warehouseNameValidator;

    @Async("warehouseExportExecutor")
    public void executeImport(Long taskId, byte[] fileBytes,
                              List<WarehouseImportAttachmentProcessor.AttachmentInput> attachments,
                              User operator) {
        try {
            taskState.setStatus(taskId, ImportTaskStatus.VALIDATING);

            WarehouseImportExcelReader.SheetData sheet = excelReader.read(fileBytes);
            String[] header = sheet.header();
            List<String> headerErrors = WarehouseImportPolicy.validateHeader(header);
            if (!headerErrors.isEmpty()) {
                taskState.fail(taskId, "表头校验失败: " + String.join("; ", headerErrors));
                return;
            }

            List<WarehouseImportRow> rows = new ArrayList<>();
            List<WarehouseImportAppService.RowError> errors = new ArrayList<>();
            List<String[]> raw = sheet.dataRows();
            for (int i = 0; i < raw.size(); i++) {
                WarehouseImportRow parsed = WarehouseImportPolicy.parseRow(i + 2, raw.get(i));
                if (parsed.valid()) {
                    rows.add(parsed);
                } else {
                    errors.add(new WarehouseImportAppService.RowError(parsed.rowIndex, String.join("; ", parsed.errors)));
                }
            }

            Set<String> existingNames = warehouseNameValidator.loadExistingNames();
            List<WarehouseImportRow> uniqueRows = new ArrayList<>();
            for (WarehouseImportRow row : rows) {
                if (existingNames.contains(row.sanitizedName)) {
                    errors.add(new WarehouseImportAppService.RowError(row.rowIndex, "仓库「" + row.sanitizedName + "」已存在，无法重复导入"));
                } else {
                    uniqueRows.add(row);
                }
            }
            rows = uniqueRows;

            taskState.updateCounts(taskId, raw.size(), rows.size(), errors.size());

            if (rows.isEmpty()) {
                taskState.completeWithErrors(taskId, errors);
                return;
            }

            taskState.setStatus(taskId, ImportTaskStatus.IMPORTING);

            Map<String, WarehouseEntity> createdBySanitizedName = new HashMap<>();
            int imported = 0;
            for (WarehouseImportRow row : rows) {
                try {
                    WarehouseEntity saved = rowPersister.persist(row, operator);
                    createdBySanitizedName.put(row.sanitizedName, saved);
                    imported++;
                } catch (RuntimeException ex) {
                    errors.add(new WarehouseImportAppService.RowError(row.rowIndex, "保存失败: " + ex.getMessage()));
                }
            }

            WarehouseImportAttachmentProcessor.AttachmentResult attachResult = attachmentProcessor
                    .attachFiles(createdBySanitizedName, rows, attachments, operator.getId());

            String correctionPath = null;
            if (!errors.isEmpty()) {
                correctionPath = correctionFileGenerator.generate(taskId, errors, sheet);
            }
            taskState.complete(taskId, imported, errors, attachResult, correctionPath);
        } catch (IOException e) {
            log.error("仓库导入读取失败: taskId={}", taskId, e);
            taskState.fail(taskId, "文件读取失败: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("仓库导入执行失败: taskId={}", taskId, e);
            taskState.fail(taskId, StringUtils.truncate(e.getMessage(), 500));
        } catch (Error e) {
            // CO-469 第四轮教训：必须 catch Error，否则线程池线程被杀死，任务永远卡 PENDING/PROCESSING
            log.error("仓库导入遭遇 Error，尝试标记失败: taskId={}", taskId, e);
            taskState.fail(taskId, "系统资源异常: " + e.getClass().getSimpleName());
            throw e;
        }
    }
}
