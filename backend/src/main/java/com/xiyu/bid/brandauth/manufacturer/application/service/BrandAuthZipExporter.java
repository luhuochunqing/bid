package com.xiyu.bid.brandauth.manufacturer.application.service;

import com.xiyu.bid.brandauth.manufacturer.domain.BrandAuthAttachmentFilter;
import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthAttachmentEntity;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthAttachmentJpaRepository;
import com.xiyu.bid.common.util.ZipEntryDeduplicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 品牌授权 ZIP 导出服务（含附件）.
 * 打包结构：
 *   _台账.xlsx  — 品牌授权台账 Excel
 *   品牌A_1/AUTH_DOC_xxx.pdf
 *   品牌A_1/SUPPLEMENTARY_yyy.pdf
 *   品牌B_2/...
 *   _导出报告.txt — 成功/失败清单
 */
@Service
@RequiredArgsConstructor
@Slf4j
public final class BrandAuthZipExporter {

    /** 单次导出附件总数上限（OOM 保护）. */
    static final int MAX_TOTAL_ATTACHMENTS = 500;

    private final ListManufacturerAuthAppService listService;
    private final BrandAuthAttachmentJpaRepository attachmentRepository;
    private final AttachmentUploadAppService attachmentStorageService;
    private final BrandAuthExportService excelExporter;

    /**
     * 导出 ZIP（含 Excel 台账 + 附件 + 导出报告）.
     *
     * @param filter 查询条件（与列表页共享 Specification）
     * @param attachmentTypes 附件类型筛选；null 或空 = 全量
     * @return ZIP 字节数组
     * @throws IOException IO 异常
     */
    public byte[] exportZip(
            final ListManufacturerAuthAppService.ListFilter filter,
            final List<String> attachmentTypes)
            throws IOException {
        BrandAuthAttachmentFilter.validateTypes(attachmentTypes);
        List<ManufacturerAuthorization> all =
                listService.listAllForExport(filter);

        byte[] excelBytes = excelExporter.exportByFilter(filter);

        // 批量查询所有附件，避免 N+1 查询
        List<Long> authIds = all.stream().map(ManufacturerAuthorization::id).toList();
        Map<Long, List<BrandAuthAttachmentEntity>> attMap = attachmentRepository
                .findByAuthorizationIdIn(authIds).stream()
                .collect(Collectors.groupingBy(
                        BrandAuthAttachmentEntity::getAuthorizationId));

        int totalAttachments = 0;
        int successCount = 0;
        int failedCount = 0;
        var failedRecords = new java.util.ArrayList<String>();

        var out = new ByteArrayOutputStream();
        try (var zipOut = new ZipOutputStream(out)) {
            // 1. 写入 Excel 台账
            ZipEntry excelEntry = new ZipEntry("_台账.xlsx");
            zipOut.putNextEntry(excelEntry);
            zipOut.write(excelBytes);
            zipOut.closeEntry();

            // 2. 遍历记录，按品牌名称分文件夹打包附件
            ZipEntryDeduplicator dedup = new ZipEntryDeduplicator();
            for (int i = 0; i < all.size(); i++) {
                ManufacturerAuthorization auth = all.get(i);
                String folderName = safeFolderName(auth.brandName())
                        + "_" + (i + 1);
                List<BrandAuthAttachmentEntity> atts =
                        attMap.getOrDefault(auth.id(), List.of());
                // 筛选附件类型（使用纯函数）
                atts = atts.stream()
                        .filter(a -> BrandAuthAttachmentFilter.matches(
                                a.getAttachmentType(), attachmentTypes))
                        .toList();
                if (atts.isEmpty()) {
                    continue;
                }
                // 上限保护
                totalAttachments += atts.size();
                if (totalAttachments > MAX_TOTAL_ATTACHMENTS) {
                    throw new IOException("导出附件总数超过上限 "
                            + MAX_TOTAL_ATTACHMENTS + "，请减少导出范围");
                }
                for (BrandAuthAttachmentEntity att : atts) {
                    String fileName = ZipEntryDeduplicator.safeFileName(
                            att.getFileName());
                    if (fileName.isEmpty()) {
                        fileName = "attachment_" + att.getId();
                    }
                    String zipPath = dedup.deduplicate(
                            folderName + "/" + fileName);
                    ZipEntry entry = new ZipEntry(zipPath);
                    zipOut.putNextEntry(entry);
                    try {
                        byte[] fileBytes = attachmentStorageService
                                .readAttachmentFile(att.getFileUrl());
                        zipOut.write(fileBytes);
                        successCount++;
                    } catch (IOException e) {
                        log.warn("读取附件失败: {} - {}",
                                att.getFileUrl(), e.getMessage());
                        zipOut.write(("读取失败: " + e.getMessage())
                                .getBytes(StandardCharsets.UTF_8));
                        failedCount++;
                        failedRecords.add(folderName + "/" + fileName
                                + " → " + e.getMessage());
                    }
                    zipOut.closeEntry();
                }
            }

            // 3. 写入导出报告
            String reportText = BrandAuthExportReportBuilder.build(
                    java.time.LocalDateTime.now(),
                    all.size(), successCount, failedCount, failedRecords);
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
        return safe.isEmpty() ? "unnamed_brand" : safe;
    }
}
