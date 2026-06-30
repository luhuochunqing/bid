package com.xiyu.bid.platform.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.infrastructure.excel.SingleSheetExcelReader;
import com.xiyu.bid.infrastructure.excel.SingleSheetExcelReader.WorkbookData;
import com.xiyu.bid.platform.domain.PlatformAccountImportPolicy;
import com.xiyu.bid.platform.domain.PlatformAccountImportPolicy.ParsedAccountRow;
import com.xiyu.bid.platform.infrastructure.persistence.entity.PlatformAccountImportTaskEntity;
import com.xiyu.bid.platform.infrastructure.persistence.repository.PlatformAccountImportTaskJpaRepository;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import com.xiyu.bid.common.util.ExcelAutoSizeHelper;
import com.xiyu.bid.repository.UserRepository;
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
public class PlatformAccountImportAppService {

    private final SingleSheetExcelReader excelReader;
    private final PlatformAccountImportRowPersister rowPersister;
    private final PlatformAccountImportTaskJpaRepository taskRepo;
    private final PlatformAccountRepository accountRepo;
    private final UserRepository userRepository;

    /** 同步创建导入任务，返回 taskId。异步执行导入。 */
    @Transactional
    public Long triggerImport(byte[] fileBytes, String filename, Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        PlatformAccountImportTaskEntity task = new PlatformAccountImportTaskEntity();
        task.setStatus("PENDING");
        task.setSourceFilename(filename);
        task.setCreatedBy(userId);
        task.setCreatedByUsername(user != null ? user.getFullName() : null);
        task = taskRepo.save(task);

        executeImportAsync(task.getId(), fileBytes, userId);
        return task.getId();
    }

    @Async
    public void executeImportAsync(Long taskId, byte[] fileBytes, Long userId) {
        PlatformAccountImportTaskEntity task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;

        try {
            // 1. 校验
            updateStatus(task, "VALIDATING");
            WorkbookData wb = excelReader.read(fileBytes);

            // 2. 解析行
            List<String> headerErrors = PlatformAccountImportPolicy.validateHeader(wb.header());
            List<String> allErrors = new ArrayList<>(headerErrors);

            List<ParsedAccountRow> rows = new ArrayList<>();
            if (headerErrors.isEmpty()) {
                for (String[] cells : wb.data()) {
                    if (isEmptyRow(cells)) continue;
                    rows.add(PlatformAccountImportPolicy.parseRow(rows.size() + 2, cells));
                }
            }

            // 3. 统计
            int total = rows.size();
            long valid = rows.stream().filter(ParsedAccountRow::valid).count();
            long invalid = total - valid;

            task.setTotalRows(total);
            task.setValidRows((int) valid);
            task.setInvalidRows((int) invalid);
            taskRepo.save(task);

            // 4. 持久化
            updateStatus(task, "IMPORTING");
            int imported = 0;
            int failed = 0;
            for (ParsedAccountRow row : rows) {
                if (!row.valid()) continue;
                try {
                    // 检查唯一性
                    if (!row.accountName().isEmpty() && accountRepo.findByAccountName(row.accountName()).isPresent()) {
                        row.errors().add("平台名称「" + row.accountName() + "」已存在");
                        failed++;
                        continue;
                    }
                    if (!row.username().isEmpty() && accountRepo.findByUsername(row.username()).isPresent()) {
                        row.errors().add("登录账号「" + row.username() + "」已存在");
                        failed++;
                        continue;
                    }
                    rowPersister.persist(row);
                    imported++;
                } catch (RuntimeException e) {
                    row.errors().add("导入失败: " + e.getMessage());
                    failed++;
                }
            }

            // 5. 完成
            List<String> errorLines = new ArrayList<>(allErrors);
            for (ParsedAccountRow row : rows) {
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

            log.info("PlatformAccount import task {} completed: {} total, {} imported, {} errors",
                    taskId, total, imported, invalid + failed);
        } catch (IOException | RuntimeException e) {
            log.error("PlatformAccount import task {} failed", taskId, e);
            updateStatus(task, "FAILED");
            task.setErrorDetails("导入失败: " + e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            taskRepo.save(task);
        }
    }

    private void updateStatus(PlatformAccountImportTaskEntity task, String status) {
        task.setStatus(status);
        taskRepo.save(task);
    }

    private boolean isEmptyRow(String[] cells) {
        for (String c : cells) if (c != null && !c.isBlank()) return false;
        return true;
    }

    /** 查询导入任务历史 */
    public List<PlatformAccountImportTaskEntity> listTasks(Long userId) {
        return taskRepo.findByCreatedByOrderByCreatedAtDesc(userId);
    }

    /** 查询单个任务 */
    public PlatformAccountImportTaskEntity getTask(Long taskId) {
        return taskRepo.findById(taskId).orElse(null);
    }

    /** 生成下载模板（单 Sheet） */
    public byte[] generateTemplate() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("平台账户导入模板");
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < PlatformAccountImportPolicy.HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(PlatformAccountImportPolicy.HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            ExcelAutoSizeHelper.autoSizeColumns(sheet, PlatformAccountImportPolicy.HEADERS.length);

            var baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }
}
