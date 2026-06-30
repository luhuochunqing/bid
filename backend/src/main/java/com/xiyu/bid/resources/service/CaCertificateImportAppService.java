package com.xiyu.bid.resources.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.infrastructure.excel.SingleSheetExcelReader;
import com.xiyu.bid.infrastructure.excel.SingleSheetExcelReader.WorkbookData;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.resources.domain.CaCertificateImportPolicy;
import com.xiyu.bid.resources.domain.CaCertificateImportPolicy.ParsedCaRow;
import com.xiyu.bid.resources.infrastructure.persistence.entity.CaCertificateImportTaskEntity;
import com.xiyu.bid.resources.infrastructure.persistence.repository.CaCertificateImportTaskJpaRepository;
import com.xiyu.bid.common.util.ExcelAutoSizeHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaCertificateImportAppService {

    private final SingleSheetExcelReader excelReader;
    private final CaCertificateImportRowPersister rowPersister;
    private final CaCertificateImportTaskJpaRepository taskRepo;
    private final UserRepository userRepository;

    /** 同步创建导入任务，返回 taskId。异步执行导入。 */
    @Transactional
    public Long triggerImport(byte[] fileBytes, String filename, String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        Long userId = user != null ? user.getId() : 0L;
        CaCertificateImportTaskEntity task = new CaCertificateImportTaskEntity();
        task.setStatus("PENDING");
        task.setSourceFilename(filename);
        task.setCreatedBy(userId);
        task.setCreatedByUsername(user != null ? user.getFullName() : username);
        task = taskRepo.save(task);

        executeImportAsync(task.getId(), fileBytes, userId);
        return task.getId();
    }

    @Async
    public void executeImportAsync(Long taskId, byte[] fileBytes, Long userId) {
        CaCertificateImportTaskEntity task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;

        try {
            // 1. 校验
            updateStatus(task, "VALIDATING");
            WorkbookData wb = excelReader.read(fileBytes);

            // 2. 解析行
            List<String> headerErrors = CaCertificateImportPolicy.validateHeader(wb.header());
            List<String> allErrors = new ArrayList<>(headerErrors);

            List<ParsedCaRow> rows = new ArrayList<>();
            if (headerErrors.isEmpty()) {
                for (String[] cells : wb.data()) {
                    if (isEmptyRow(cells)) continue;
                    rows.add(CaCertificateImportPolicy.parseRow(rows.size() + 2, cells));
                }
            }

            // 3. 统计
            int total = rows.size();
            long valid = rows.stream().filter(ParsedCaRow::valid).count();
            long invalid = total - valid;

            task.setTotalRows(total);
            task.setValidRows((int) valid);
            task.setInvalidRows((int) invalid);
            taskRepo.save(task);

            // 4. 持久化
            updateStatus(task, "IMPORTING");
            int imported = 0;
            int failed = 0;
            for (ParsedCaRow row : rows) {
                if (!row.valid()) continue;
                try {
                    rowPersister.persist(row);
                    imported++;
                } catch (RuntimeException e) {
                    row.errors().add("导入失败: " + e.getMessage());
                    failed++;
                }
            }

            // 5. 完成
            List<String> errorLines = new ArrayList<>(allErrors);
            for (ParsedCaRow row : rows) {
                if (!row.valid()) {
                    errorLines.add("第 " + row.rowIndex() + " 行: " +
                            String.join("; ", row.errors()));
                }
            }

            task.setImportedRows(imported);
            task.setUpdatedRows(0); // INSERT-only, no updates
            task.setInvalidRows((int) invalid + failed);
            task.setErrorDetails(errorLines.isEmpty() ? null : String.join("\n", errorLines));
            task.setStatus("COMPLETED");
            task.setCompletedAt(LocalDateTime.now());
            taskRepo.save(task);

            log.info("CaCertificate import task {} completed: {} total, {} imported, {} errors",
                    taskId, total, imported, invalid + failed);
        } catch (IOException | RuntimeException e) {
            log.error("CaCertificate import task {} failed", taskId, e);
            updateStatus(task, "FAILED");
            task.setErrorDetails("导入失败: " + e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            taskRepo.save(task);
        }
    }

    private void updateStatus(CaCertificateImportTaskEntity task, String status) {
        task.setStatus(status);
        taskRepo.save(task);
    }

    private boolean isEmptyRow(String[] cells) {
        for (String c : cells) if (c != null && !c.isBlank()) return false;
        return true;
    }

    /** 查询导入任务历史（Map 格式，供 Controller 使用以隔离 Entity） */
    public List<java.util.Map<String, Object>> listTasksAsMap(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        Long userId = user != null ? user.getId() : 0L;
        return taskRepo.findByCreatedByOrderByCreatedAtDesc(userId).stream()
                .map(this::toMap)
                .toList();
    }

    /** 查询单个任务（Map 格式） */
    public java.util.Map<String, Object> getTaskAsMap(Long taskId) {
        CaCertificateImportTaskEntity task = taskRepo.findById(taskId).orElse(null);
        return task == null ? null : toMap(task);
    }

    private java.util.Map<String, Object> toMap(CaCertificateImportTaskEntity t) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("status", t.getStatus());
        m.put("totalRows", t.getTotalRows());
        m.put("validRows", t.getValidRows());
        m.put("invalidRows", t.getInvalidRows());
        m.put("importedRows", t.getImportedRows());
        m.put("updatedRows", t.getUpdatedRows());
        m.put("errorDetails", t.getErrorDetails());
        m.put("sourceFilename", t.getSourceFilename());
        m.put("createdAt", t.getCreatedAt());
        m.put("completedAt", t.getCompletedAt());
        return m;
    }

    /** 生成下载模板（单 Sheet） */
    public byte[] generateTemplate() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("CA证书导入模板");
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < CaCertificateImportPolicy.HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(CaCertificateImportPolicy.HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            ExcelAutoSizeHelper.autoSizeColumns(sheet, CaCertificateImportPolicy.HEADERS.length);

            var baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }
}
