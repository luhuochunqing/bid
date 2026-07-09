package com.xiyu.bid.platform.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.domain.PlatformAccountImportPolicy;
import com.xiyu.bid.platform.infrastructure.persistence.entity.PlatformAccountImportTaskEntity;
import com.xiyu.bid.platform.infrastructure.persistence.repository.PlatformAccountImportTaskJpaRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 平台账户导入应用服务（CO-560 重构后）。
 *
 * <p><b>职责</b>：仅负责同步阶段（创建 PENDING 任务记录）+ 查询/模板生成。
 * 异步执行已提取到 {@link PlatformAccountImportAsyncRunner}，避免 @Async 自调用导致代理失效。
 *
 * <p><b>根因</b>：原 {@code executeImportAsync} 通过 {@code this.} 自调用，Spring AOP 代理失效，
 * {@code @Async} 不生效，同步执行在 HTTP 请求线程内，与 {@code triggerImport} 的 {@code @Transactional}
 * 共享同一事务，单行 DB 异常 → Hibernate Session 中毒 + UnexpectedRollbackException。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAccountImportAppService {

    private final PlatformAccountImportAsyncRunner asyncRunner;
    private final PlatformAccountImportTaskJpaRepository taskRepo;
    private final UserRepository userRepository;

    /** 同步创建导入任务，返回 taskId。异步执行导入（跨类调用，@Async 代理生效）。 */
    @Transactional
    public Long triggerImport(byte[] fileBytes, String filename, Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        PlatformAccountImportTaskEntity task = new PlatformAccountImportTaskEntity();
        task.setStatus("PENDING");
        task.setSourceFilename(filename);
        task.setCreatedBy(userId);
        task.setCreatedByUsername(user != null ? user.getFullName() : null);
        task = taskRepo.save(task);

        asyncRunner.executeImportAsync(task.getId(), fileBytes, userId);
        return task.getId();
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
