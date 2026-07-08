// Input: qualification list and access scope
// Output: Excel / ZIP / template exports
// Pos: Service/业务支撑层
// 维护声明: 仅维护资质导出与模板生成；CRUD 在 QualificationService。
package com.xiyu.bid.qualification.service;

import com.xiyu.bid.common.util.ZipEntryDeduplicator;
import com.xiyu.bid.exception.InvalidArgumentException;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 资质 Excel 导出 / 模板生成 / ZIP 附件打包。
 * 从 QualificationService 拆出以控行数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualificationExportService {

    private final QualificationFlatQuery flatQuery;
    private final QualificationExcelSupport qualificationExcelSupport;

    @Value("${qualification.attachment.storage-path:data/qualification-attachments}")
    private String storageRoot;

    private static final String[] EXPORT_COLS = {
            "证书名称", "等级", "认证机构", "证书编号", "发证日期", "有效期",
            "代理机构", "代理机构联系人", "认证范围", "状态"
    };
    private static final String[] TEMPLATE_COLS = {
            "证书名称", "等级", "认证机构", "证书编号", "发证日期", "证书有效期",
            "代理机构", "代理机构联系人", "认证范围", "证书审核提醒", "附件文件名",
            "审核日志附件文件名"
    };

    public void exportExcel(String keyword, String status, OutputStream out) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var sh = wb.createSheet("资质证书台账");
            var hr = sh.createRow(0);
            for (int i = 0; i < EXPORT_COLS.length; i++) {
                hr.createCell(i).setCellValue(EXPORT_COLS[i]);
            }
            List<String> statusFilter = status == null ? null : List.of(status);
            List<QualificationDTO> all = flatQuery.listAll(keyword, statusFilter);
            int r = 1;
            for (var q : all) {
                var row = sh.createRow(r++);
                row.createCell(0).setCellValue(nullToEmpty(q.getName()));
                row.createCell(1).setCellValue(nullToEmpty(q.getLevel()));
                row.createCell(2).setCellValue(nullToEmpty(q.getIssuer()));
                row.createCell(3).setCellValue(nullToEmpty(q.getCertificateNo()));
                row.createCell(4).setCellValue(q.getIssueDate() != null ? q.getIssueDate().toString() : "");
                row.createCell(5).setCellValue(q.getExpiryDate() != null ? q.getExpiryDate().toString() : "");
                row.createCell(6).setCellValue(nullToEmpty(q.getAgency()));
                row.createCell(7).setCellValue(nullToEmpty(q.getAgencyContact()));
                row.createCell(8).setCellValue(nullToEmpty(q.getCertScope()));
                row.createCell(9).setCellValue(statusLabel(q.getStatus()));
            }
            wb.write(out);
        }
    }

    public void generateTemplate(OutputStream out) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var sh = wb.createSheet("资质证书");
            var hr = sh.createRow(0);
            for (int i = 0; i < TEMPLATE_COLS.length; i++) {
                hr.createCell(i).setCellValue(TEMPLATE_COLS[i]);
            }
            wb.write(out);
        }
    }

    @Transactional(readOnly = true)
    public byte[] batchExportExcel(List<Long> ids) throws IOException {
        if (ids == null || ids.isEmpty()) {
            throw new InvalidArgumentException("导出 ID 列表不能为空");
        }
        // CO-471 fix: Spring MVC @RequestBody Map<String, List<Long>> 因 Jackson 类型擦除
        // 实际反序列化为 List<Integer>。直接用 ids.stream() 会在 accept 时 cast 元素到
        // Long 而 ClassCastException，因此用 raw type + Number.longValue() 统一转 long。
        Set<Long> idSet = toLongSet(ids);
        List<QualificationDTO> items = flatQuery.listAll(null, null).stream()
                .filter(q -> idSet.contains(q.getId()))
                .toList();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        qualificationExcelSupport.writeLedger(items, null, out);
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] batchExportZip(List<Long> ids) throws IOException {
        if (ids == null || ids.isEmpty()) {
            throw new InvalidArgumentException("下载 ID 列表不能为空");
        }
        // CO-471 fix: 同 batchExportExcel，用 toLongSet 规避 Jackson 类型擦除。
        Set<Long> idSet = toLongSet(ids);
        List<QualificationDTO> items = flatQuery.listAll(null, null).stream()
                .filter(q -> idSet.contains(q.getId()))
                .toList();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Sentry 7590982843: 多条资质的附件可能同名，需用 ZipEntryDeduplicator 去重避免
        // ZipException: duplicate entry。同时用 safeFileName 清理文件名非法字符。
        ZipEntryDeduplicator dedup = new ZipEntryDeduplicator();
        int writtenEntries = 0;
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (QualificationDTO item : items) {
                if (item.getAttachments() != null) {
                    for (var att : item.getAttachments()) {
                        if (att.getFileUrl() == null || att.getFileUrl().isBlank()) continue;
                        String entryName = buildEntryName(item.getName(),
                                att.getFileName() != null ? att.getFileName() : att.getFileUrl());
                        if (writeAttachmentToZip(zos, item.getId(), att.getFileUrl(), entryName, dedup)) {
                            writtenEntries++;
                        }
                    }
                }
                if (item.getFileUrl() != null && !item.getFileUrl().isBlank()) {
                    String entryName = buildEntryName(item.getName(), extractFileName(item.getFileUrl()));
                    if (writeAttachmentToZip(zos, item.getId(), item.getFileUrl(), entryName, dedup)) {
                        writtenEntries++;
                    }
                }
            }
        }
        if (writtenEntries == 0) {
            throw new InvalidArgumentException("所选资质证书均无可下载附件");
        }
        return out.toByteArray();
    }

    /**
     * 构建 ZIP entry 名称，并清理文件名中的非法字符。
     */
    private static String buildEntryName(String qualName, String fileName) {
        String safeQual = ZipEntryDeduplicator.safeFileName(qualName);
        if (safeQual.isEmpty()) safeQual = "资质";
        String safeFile = ZipEntryDeduplicator.safeFileName(fileName);
        if (safeFile.isEmpty()) safeFile = "attachment";
        return safeQual + "_" + safeFile;
    }

    /**
     * CO-471 fix: 将 ids 转为 Set<Long>。
     * 用 raw type 遍历，避免 Stream 在 accept 时把 Integer cast 到 Long 抛 ClassCastException。
     * Integer 和 Long 都是 Number，用 Number.longValue() 统一取 long 值。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Set<Long> toLongSet(List<Long> ids) {
        Set<Long> idSet = new HashSet<>();
        for (Object id : (List) ids) {
            if (id instanceof Number n) {
                idSet.add(n.longValue());
            } else if (id != null) {
                idSet.add(Long.parseLong(id.toString()));
            }
        }
        return idSet;
    }

    private boolean writeAttachmentToZip(ZipOutputStream zos, Long qualificationId, String fileUrl,
                                         String entryName, ZipEntryDeduplicator dedup) throws IOException {
        // 优先从本地文件系统读取（fileUrl 可能是 /api/knowledge/qualifications/{id}/attachments/{fileName}）
        Path localPath = resolveLocalPath(qualificationId, fileUrl);
        if (localPath != null && Files.exists(localPath) && !Files.isDirectory(localPath)) {
            zos.putNextEntry(new ZipEntry(dedup.deduplicate(entryName)));
            Files.copy(localPath, zos);
            zos.closeEntry();
            return true;
        }
        // 回退：仅当 fileUrl 是绝对 URL（http/https）时才尝试网络下载。
        // fileUrl 在 DB 中实际存的是裸文件名（BatchAttachmentService.setFileUrl(uniqueFilename)），
        // 本地缺失时 URI.create(fileUrl).toURL() 会抛 IllegalArgumentException: URI is not absolute，
        // 必须显式捕获，否则会逃逸到 GlobalExceptionHandler 被映射为 400。
        try (InputStream in = URI.create(fileUrl).toURL().openStream()) {
            zos.putNextEntry(new ZipEntry(dedup.deduplicate(entryName)));
            in.transferTo(zos);
            zos.closeEntry();
            return true;
        } catch (MalformedURLException | IllegalArgumentException e) {
            // 跳过无法下载的附件，不再写入 .txt 占位文件污染 ZIP。
            // 最终无有效附件时由调用方抛出 InvalidArgumentException 给出明确提示。
            log.warn("资质[{}]附件无法下载（本地文件不存在或非绝对URL）：{}", qualificationId, fileUrl);
            return false;
        }
    }

    private Path resolveLocalPath(Long qualificationId, String fileUrl) {
        if (fileUrl == null || qualificationId == null) return null;
        String fileName;
        String apiPrefix = "/api/knowledge/qualifications/";
        if (fileUrl.startsWith(apiPrefix)) {
            String rest = fileUrl.substring(apiPrefix.length());
            int attachIdx = rest.indexOf("/attachments/");
            if (attachIdx > 0) {
                fileName = rest.substring(attachIdx + "/attachments/".length());
            } else {
                fileName = fileUrl;
            }
        } else {
            fileName = fileUrl;
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            return null;
        }
        Path baseDir = getStorageRoot();
        Path resolved = baseDir.resolve(String.valueOf(qualificationId)).resolve(fileName).normalize();
        if (!resolved.startsWith(baseDir)) return null;
        return resolved;
    }

    private Path getStorageRoot() {
        return Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    private String extractFileName(String fileUrl) {
        if (fileUrl == null) return "attachment";
        int slash = Math.max(fileUrl.lastIndexOf('/'), fileUrl.lastIndexOf('\\'));
        return slash >= 0 ? fileUrl.substring(slash + 1) : fileUrl;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String statusLabel(String status) {
        if (status == null) return "";
        return switch (status.toLowerCase()) {
            case "valid", "in_stock" -> "在库";
            case "expiring" -> "即将到期";
            case "expired" -> "已过期";
            case "retired" -> "已下架";
            default -> status;
        };
    }
}
