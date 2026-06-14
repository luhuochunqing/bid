// Input: qualification list and access scope
// Output: Excel / ZIP / template exports
// Pos: Service/业务支撑层
// 维护声明: 仅维护资质导出与模板生成；CRUD 在 QualificationService。
package com.xiyu.bid.qualification.service;

import com.xiyu.bid.exception.InvalidArgumentException;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 资质 Excel 导出 / 模板生成 / ZIP 附件打包。
 * 从 QualificationService 拆出以控行数。
 */
@Service
@RequiredArgsConstructor
public class QualificationExportService {

    private final QualificationFlatQuery flatQuery;
    private final QualificationExcelSupport qualificationExcelSupport;

    private static final String[] EXPORT_COLS = {
            "证书名称", "等级", "认证机构", "证书编号", "发证日期", "有效期",
            "代理机构", "代理联系方式", "认证范围", "状态"
    };
    private static final String[] TEMPLATE_COLS = {
            "证书名称", "等级", "认证机构", "证书编号", "发证日期", "证书有效期",
            "代理机构", "代理联系方式", "认证范围", "证书审核提醒", "附件文件名"
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
        List<QualificationDTO> items = flatQuery.listAll(null, null).stream()
                .filter(q -> ids.contains(q.getId()))
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
        List<QualificationDTO> items = flatQuery.listAll(null, null).stream()
                .filter(q -> ids.contains(q.getId()))
                .toList();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (QualificationDTO item : items) {
                if (item.getAttachments() != null) {
                    for (var att : item.getAttachments()) {
                        if (att.getFileUrl() == null || att.getFileUrl().isBlank()) continue;
                        String entryName = (item.getName() == null ? "资质" : item.getName()) + "_"
                                + (att.getFileName() == null ? att.getFileUrl() : att.getFileName());
                        writeAttachmentToZip(zos, item.getId(), att.getFileUrl(), entryName);
                    }
                }
                if (item.getFileUrl() != null && !item.getFileUrl().isBlank()) {
                    String entryName = (item.getName() == null ? "资质" : item.getName()) + "_" + extractFileName(item.getFileUrl());
                    writeAttachmentToZip(zos, item.getId(), item.getFileUrl(), entryName);
                }
            }
        }
        return out.toByteArray();
    }

    private void writeAttachmentToZip(ZipOutputStream zos, Long qualificationId, String fileUrl, String entryName) throws IOException {
        // 优先从本地文件系统读取（fileUrl 可能是 /api/knowledge/qualifications/{id}/attachments/{fileName}）
        Path localPath = resolveLocalPath(qualificationId, fileUrl);
        if (localPath != null && Files.exists(localPath) && !Files.isDirectory(localPath)) {
            zos.putNextEntry(new ZipEntry(entryName));
            Files.copy(localPath, zos);
            zos.closeEntry();
            return;
        }
        // 回退：如果是完整 URL（http/https），尝试作为 URL 打开
        try (InputStream in = URI.create(fileUrl).toURL().openStream()) {
            zos.putNextEntry(new ZipEntry(entryName));
            in.transferTo(zos);
            zos.closeEntry();
        } catch (MalformedURLException e) {
            zos.putNextEntry(new ZipEntry(entryName + ".txt"));
            zos.write(("无法下载: " + fileUrl).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private Path resolveLocalPath(Long qualificationId, String fileUrl) {
        if (fileUrl == null || qualificationId == null) return null;
        String fileName;
        // 格式: /api/knowledge/qualifications/{id}/attachments/{fileName}
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
        // 路径遍历防护
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            return null;
        }
        Path baseDir = Paths.get("data/qualification-attachments").toAbsolutePath().normalize();
        Path resolved = baseDir.resolve(String.valueOf(qualificationId)).resolve(fileName).normalize();
        if (!resolved.startsWith(baseDir)) return null;
        return resolved;
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
