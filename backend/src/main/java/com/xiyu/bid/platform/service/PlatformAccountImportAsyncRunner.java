package com.xiyu.bid.platform.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.infrastructure.excel.SingleSheetExcelReader;
import com.xiyu.bid.infrastructure.excel.SingleSheetExcelReader.WorkbookData;
import com.xiyu.bid.platform.domain.PlatformAccountImportErrorMessageTranslator;
import com.xiyu.bid.platform.domain.PlatformAccountImportPolicy;
import com.xiyu.bid.platform.domain.PlatformAccountImportPolicy.ParsedAccountRow;
import com.xiyu.bid.platform.infrastructure.persistence.entity.PlatformAccountImportTaskEntity;
import com.xiyu.bid.platform.infrastructure.persistence.repository.PlatformAccountImportTaskJpaRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 平台账户导入异步执行器（CO-560）。
 *
 * <p><b>根因</b>：原 {@link PlatformAccountImportAppService#executeImportAsync} 通过 {@code this.}
 * 自调用，Spring AOP 代理失效 → {@code @Async} 不生效 → 异步方法同步执行在 HTTP 请求线程内，
 * 且与 {@code triggerImport} 的 {@code @Transactional} 共享同一事务，导致单行 DB 异常触发
 * Hibernate Session 中毒 + UnexpectedRollbackException。
 *
 * <p><b>修复</b>：提取到独立 Bean，由 {@link PlatformAccountImportAppService#triggerImport}
 * 跨类调用，Spring 代理生效 → {@code @Async} 真正异步执行。
 * 复用 §31 模式：{@code @Async("tenderImportExecutor")} + {@link com.xiyu.bid.config.MdcTaskDecorator} 透传 MDC。
 *
 * <p><b>事务边界</b>：本方法不加 {@code @Transactional}（避免跨整个异步执行的长事务）。
 * 每行持久化由 {@link PlatformAccountImportRowPersister#persist} 的 {@code REQUIRES_NEW} 独立事务处理。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAccountImportAsyncRunner {

    private final SingleSheetExcelReader excelReader;
    private final PlatformAccountImportRowPersister rowPersister;
    private final PlatformAccountImportTaskJpaRepository taskRepo;
    private final UserRepository userRepository;

    /**
     * 异步执行导入任务。
     *
     * <p>使用 {@code tenderImportExecutor} 专用线程池（spec 031 新增，挂载 MdcTaskDecorator）。
     * 不加 {@code @Transactional}：每行由 {@link PlatformAccountImportRowPersister#persist} 独立事务。
     * 任务状态更新使用 {@code taskRepo.save()} 即可（auto-commit）。
     *
     * @param taskId   任务 ID
     * @param fileBytes Excel 文件字节（Controller 同步阶段已读取为 byte[]，绕开 MultipartFile 生命周期）
     * @param userId   操作人 ID
     */
    @Async("tenderImportExecutor")
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

            // 4. 持久化（每行独立事务，REQUIRES_NEW）
            updateStatus(task, "IMPORTING");
            int imported = 0;
            int failed = 0;
            for (ParsedAccountRow row : rows) {
                if (!row.valid()) continue;
                try {
                    User custodian = userRepository.findByEmployeeNumber(row.employeeNumber()).orElse(null);
                    if (custodian == null) {
                        row.errors().add("工号「" + row.employeeNumber() + "」未匹配到用户");
                        failed++;
                        continue;
                    }
                    rowPersister.persist(row, custodian.getId());
                    imported++;
                } catch (RuntimeException e) {
                    // CO-560: REQUIRES_NEW 传播下，单行失败只回滚该行事务，不影响外层
                    // CO-560 补强：异常消息翻译，不向用户暴露表结构/列名
                    log.warn("PlatformAccount import row {} failed: {}", row.rowIndex(), e.getMessage());
                    row.errors().add(PlatformAccountImportErrorMessageTranslator.translate(e));
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
}
