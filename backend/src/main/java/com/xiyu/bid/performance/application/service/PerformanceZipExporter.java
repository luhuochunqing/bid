package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.common.util.ZipEntryDeduplicator;
import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.dto.PerformanceExportCriteria;
import com.xiyu.bid.performance.application.exception.PerformanceExportException;
import com.xiyu.bid.performance.application.mapper.PerformanceMapper;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.port.PerformanceAlertConfigRepository;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import com.xiyu.bid.performance.domain.AttachmentFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 业绩 ZIP 导出服务（含附件）.
 * 打包结构：
 *   _台账.xlsx  — 业绩台账 Excel
 *   合同名称_1/合同协议.pdf
 *   合同名称_1/商城截图.png
 *   合同名称_2/...
 *   _导出报告.txt — 成功/失败清单
 */
@Service
@RequiredArgsConstructor
@Slf4j
public final class PerformanceZipExporter {

    /** 业绩仓储. */
    private final PerformanceRepository repository;
    /** 业绩 Mapper. */
    private final PerformanceMapper mapper;
    /** 提醒配置仓储. */
    private final PerformanceAlertConfigRepository alertConfigRepository;
    /** Excel 导出器. */
    private final PerformanceExcelExporter excelExporter;
    /** 附件文件读取服务. */
    private final PerformanceAttachmentStorageAppService attachmentStorageService;

    /** 默认提醒配置. */
    private static final PerformanceAlertConfig DEFAULT_CONFIG =
            new PerformanceAlertConfig(null, 180, 90, true);

    /** 单次导出附件总数上限（OOM 保护）. */
    static final int MAX_TOTAL_ATTACHMENTS = 500;

    /**
     * 导出 ZIP（含 Excel 台账 + 附件 + 导出报告）.
     *
     * @param ids 记录 ID 列表，null 或空表示按 criteria 全量导出
     * @param criteria 搜索条件，null 时退化为 PerformanceSearchCriteria.empty()
     * @param exportCriteria 导出条件（附件类型筛选等），null 时退化为全量
     * @return ZIP 字节数组
     * @throws IOException IO 异常
     * @throws PerformanceExportException 附件总数超上限
     */
    public byte[] exportZip(final List<Long> ids,
                            final PerformanceSearchCriteria criteria,
                            final PerformanceExportCriteria exportCriteria)
            throws IOException {
        PerformanceExportCriteria effectiveExportCriteria = exportCriteria != null
                ? exportCriteria : PerformanceExportCriteria.allTypes();

        List<PerformanceDTO> records;
        if (ids != null && !ids.isEmpty()) {
            records = ids.stream()
                    .map(id -> mapper.toDTO(repository.findById(id).orElse(null)))
                    .filter(r -> r != null)
                    .toList();
        } else {
            var config = alertConfigRepository.findActive()
                    .orElse(DEFAULT_CONFIG);
            var effectiveCriteria = criteria != null
                    ? criteria
                    : PerformanceSearchCriteria.empty();
            records = repository.findAll(effectiveCriteria, config)
                    .stream()
                    .map(mapper::toDTO)
                    .toList();
        }

        byte[] excelBytes = excelExporter.export(ids, criteria);

        var failedAttachments = new ArrayList<ExportReportBuilder.FailedAttachmentRecord>();
        int totalAttachments = 0;
        int successCount = 0;

        var out = new ByteArrayOutputStream();
        try (var zipOut = new ZipOutputStream(out)) {
            // 1. 写入 Excel 台账
            ZipEntry excelEntry = new ZipEntry("_台账.xlsx");
            zipOut.putNextEntry(excelEntry);
            zipOut.write(excelBytes);
            zipOut.closeEntry();

            // 2. 遍历记录，按合同名称分文件夹打包（过滤后）附件
            ZipEntryDeduplicator dedup = new ZipEntryDeduplicator();
            for (int i = 0; i < records.size(); i++) {
                PerformanceDTO record = records.get(i);
                String folderName = safeFolderName(record.contractName())
                        + "_" + (i + 1);
                List<PerformanceDTO.AttachmentDTO> attachments = AttachmentFilter.filterByTypes(
                        record.attachments() != null ? record.attachments() : List.of(),
                        effectiveExportCriteria.attachmentTypes());
                if (attachments.isEmpty()) {
                    continue;
                }
                // 上限保护：累计过滤后附件总数，超限抛异常
                totalAttachments += attachments.size();
                if (totalAttachments > MAX_TOTAL_ATTACHMENTS) {
                    throw new PerformanceExportException(
                            "导出附件总数超过上限 " + MAX_TOTAL_ATTACHMENTS
                                    + "，请减少导出范围");
                }
                for (PerformanceDTO.AttachmentDTO att : attachments) {
                    if (att.fileUrl() == null
                            || att.fileUrl().isBlank()) {
                        continue;
                    }
                    String fileName = ZipEntryDeduplicator.safeFileName(att.fileName());
                    if (fileName.isEmpty()) {
                        fileName = "attachment_" + att.id();
                    }
                    String zipPath = dedup.deduplicate(folderName + "/" + fileName);

                    ZipEntry entry = new ZipEntry(zipPath);
                    zipOut.putNextEntry(entry);

                    try {
                        byte[] fileBytes = attachmentStorageService.readAttachmentFile(att.fileUrl());
                        zipOut.write(fileBytes);
                        successCount++;
                    } catch (IOException e) {
                        log.warn("读取附件失败: {} - {}",
                                att.fileUrl(), e.getMessage());
                        zipOut.write(("读取失败: " + e.getMessage())
                                .getBytes(StandardCharsets.UTF_8));
                        failedAttachments.add(new ExportReportBuilder.FailedAttachmentRecord(
                                record.contractName(), att.fileType(),
                                att.fileName(), e.getMessage()));
                    }
                    zipOut.closeEntry();
                }
            }

            // 3. 写入导出报告
            String reportText = ExportReportBuilder.build(
                    LocalDateTime.now(),
                    records.size(),
                    effectiveExportCriteria.attachmentTypes(),
                    successCount + failedAttachments.size(),
                    successCount,
                    failedAttachments);
            ZipEntry reportEntry = new ZipEntry("_导出报告.txt");
            zipOut.putNextEntry(reportEntry);
            zipOut.write(reportText.getBytes(StandardCharsets.UTF_8));
            zipOut.closeEntry();

            zipOut.finish();
        }
        return out.toByteArray();
    }

    private static String safeFolderName(String name) {
        String safe = ZipEntryDeduplicator.safeFileName(name);
        return safe.isEmpty() ? "unnamed_contract" : safe;
    }

}
