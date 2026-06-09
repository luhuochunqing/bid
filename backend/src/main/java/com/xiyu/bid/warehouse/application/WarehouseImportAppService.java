package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.warehouse.domain.ImportTaskStatus;
import com.xiyu.bid.warehouse.domain.WarehouseImportPolicy;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportExcelReader;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仓库批量导入应用服务 — 编排 Excel 解析、校验、写入和附件归档。
 * 业务校验在 WarehouseImportPolicy 纯核心；
 * 行持久化在 WarehouseImportRowPersister；
 * 附件归档在 WarehouseImportAttachmentProcessor；
 * 状态机在 WarehouseImportTaskStateService；
 * 修正文件生成在 WarehouseImportCorrectionFileGenerator。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseImportAppService {

    private final WarehouseImportTaskRepository importTaskRepo;
    private final WarehouseImportExcelReader excelReader;
    private final WarehouseImportRowPersister rowPersister;
    private final WarehouseImportAttachmentProcessor attachmentProcessor;
    private final WarehouseImportCorrectionFileGenerator correctionFileGenerator;
    private final WarehouseImportTaskStateService taskState;

    @Transactional
    public ImportTaskResult triggerImport(MultipartFile file, MultipartFile[] attachments, User operator) {
        WarehouseImportTaskEntity task = WarehouseImportTaskEntity.builder()
                .status(ImportTaskStatus.PENDING)
                .sourceFilename(file.getOriginalFilename())
                .createdBy(operator.getId())
                .createdByUsername(operator.getFullName() + "(" + operator.getUsername() + ")")
                .createdAt(LocalDateTime.now())
                .build();
        importTaskRepo.save(task);

        executeImportAsync(task.getId(), file, attachments, operator);

        return new ImportTaskResult(task.getId());
    }

    @Async("warehouseExportExecutor")
    public void executeImportAsync(Long taskId, MultipartFile file, MultipartFile[] attachments, User operator) {
        try {
            taskState.setStatus(taskId, ImportTaskStatus.VALIDATING);

            WarehouseImportExcelReader.SheetData sheet = excelReader.read(file);
            String[] header = sheet.header();
            List<String> headerErrors = WarehouseImportPolicy.validateHeader(header);
            if (!headerErrors.isEmpty()) {
                taskState.fail(taskId, "表头校验失败: " + String.join("; ", headerErrors));
                return;
            }

            List<WarehouseImportPolicy.ParsedRow> rows = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            List<String[]> raw = sheet.dataRows();
            for (int i = 0; i < raw.size(); i++) {
                WarehouseImportPolicy.ParsedRow parsed = WarehouseImportPolicy.parseRow(i + 2, raw.get(i));
                if (parsed.valid()) {
                    rows.add(parsed);
                } else {
                    errors.add(new RowError(parsed.rowIndex, String.join("; ", parsed.errors)));
                }
            }

            taskState.updateCounts(taskId, raw.size(), rows.size(), errors.size());

            if (rows.isEmpty()) {
                taskState.completeWithErrors(taskId, errors);
                return;
            }

            taskState.setStatus(taskId, ImportTaskStatus.IMPORTING);

            Map<String, WarehouseEntity> createdBySanitizedName = new HashMap<>();
            int imported = 0;
            for (WarehouseImportPolicy.ParsedRow row : rows) {
                try {
                    WarehouseEntity saved = rowPersister.persist(row, operator);
                    createdBySanitizedName.put(row.sanitizedName, saved);
                    imported++;
                } catch (RuntimeException ex) {
                    errors.add(new RowError(row.rowIndex, "保存失败: " + ex.getMessage()));
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
            taskState.fail(taskId, truncate(e.getMessage(), 500));
        }
    }

    public Page<WarehouseImportTaskEntity> listTasks(Long userId, Pageable pageable) {
        return taskState.listTasks(userId, pageable);
    }

    public WarehouseImportTaskEntity getTask(Long taskId, Long userId) {
        return taskState.getTask(taskId, userId);
    }

    public byte[] getCorrectionFile(Long taskId, Long userId) throws IOException {
        return taskState.getCorrectionFile(taskId, userId);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    public record RowError(int rowIndex, String message) {}

    public record ImportTaskResult(Long taskId) {}
}
