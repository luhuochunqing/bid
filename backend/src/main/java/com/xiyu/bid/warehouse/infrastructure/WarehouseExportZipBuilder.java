package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 仓库导出 ZIP 打包器：将 xlsx + 附件目录打包为 ZIP 文件。
 *  - 顶层：仓库信息台账.xlsx
 *  - 附件：attachments/WH_{仓库名称}_{附件类型}[_{序号}].{扩展名}
 */
@Component
@Slf4j
public class WarehouseExportZipBuilder {

    private static final String XLSX_NAME = "仓库信息台账.xlsx";
    private static final String ATTACH_DIR = "attachments/";

    @Value("${warehouse.attachment.root:/data/attachments/warehouse}")
    private String attachmentRoot;

    public ZipBuildResult buildZip(byte[] xlsxBytes,
                                   List<WarehouseEntity> entities,
                                   Map<Long, List<WarehouseAttachmentEntity>> attachmentsByWhId) throws IOException {
        Path tempDir = Files.createTempDirectory("warehouse-export-zip-");
        Path zipFile = tempDir.resolve("export.zip");
        ZipStats stats = new ZipStats();

        try (OutputStream fos = Files.newOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // 1) 写入 xlsx
            ZipEntry xlsxEntry = new ZipEntry(XLSX_NAME);
            zos.putNextEntry(xlsxEntry);
            zos.write(xlsxBytes);
            zos.closeEntry();
            stats.xlsxBytes = xlsxBytes.length;

            // 2) 写入所有附件
            for (WarehouseEntity e : entities) {
                List<WarehouseAttachmentEntity> attachments = attachmentsByWhId.getOrDefault(e.getId(), List.of());
                for (WarehouseAttachmentEntity att : attachments) {
                    String zipPath = zipEntryPath(e, att, stats);
                    if (zipPath == null) continue;
                    try {
                        Path source = Paths.get(attachmentRoot, String.valueOf(att.getWarehouse().getId()), att.getStoredFilename());
                        if (!Files.exists(source)) {
                            log.warn("仓库导出附件源文件缺失: warehouseId={}, storedFilename={}",
                                    att.getWarehouse().getId(), att.getStoredFilename());
                            continue;
                        }
                        ZipEntry entry = new ZipEntry(zipPath);
                        zos.putNextEntry(entry);
                        try (InputStream in = Files.newInputStream(source)) {
                            in.transferTo(zos);
                        }
                        zos.closeEntry();
                        countByType(stats, att.getType());
                    } catch (IOException ex) {
                        log.warn("仓库导出附件归档失败: zipPath={}, error={}", zipPath, ex.getMessage());
                    }
                }
            }
        }

        long totalBytes = Files.size(zipFile);
        return new ZipBuildResult(zipFile, totalBytes, stats);
    }

    /**
     * 计算 ZIP 内附件路径：WH_{仓库名称}_{附件类型}[_{序号}].{扩展名}。
     * 同一仓库同一类型多个附件按出现顺序加 _01, _02 ...
     */
    private String zipEntryPath(WarehouseEntity e, WarehouseAttachmentEntity att, ZipStats stats) {
        String typeLabel = typeLabel(att.getType());
        if (typeLabel == null) return null;
        String baseName = "WH_" + e.getName() + "_" + typeLabel;
        String ext = extractExt(att.getOriginalFilename());
        int seq = stats.nextSequence(e.getId(), att.getType());
        String suffix = seq == 0 ? "" : String.format("_%02d", seq);
        return ATTACH_DIR + baseName + suffix + (ext.isEmpty() ? "" : "." + ext);
    }

    private void countByType(ZipStats stats, WarehouseAttachmentType type) {
        if (type == WarehouseAttachmentType.PROPERTY_CERTIFICATE) stats.propertyCertCount++;
        else if (type == WarehouseAttachmentType.INVOICE) stats.invoiceCount++;
        else if (type == WarehouseAttachmentType.PHOTOS) stats.photosCount++;
    }

    private static String typeLabel(WarehouseAttachmentType type) {
        if (type == WarehouseAttachmentType.PROPERTY_CERTIFICATE) return "产权证";
        if (type == WarehouseAttachmentType.INVOICE) return "发票";
        if (type == WarehouseAttachmentType.PHOTOS) return "内外照片";
        return null;
    }

    private static String extractExt(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1) ? filename.substring(dot + 1).toLowerCase() : "";
    }

    public record ZipBuildResult(Path zipFile, long totalBytes, ZipStats stats) {}

    public static class ZipStats {
        public long xlsxBytes;
        public int propertyCertCount;
        public int invoiceCount;
        public int photosCount;
        private final Map<Long, Map<WarehouseAttachmentType, Integer>> sequences = new java.util.HashMap<>();

        public synchronized int nextSequence(Long warehouseId, WarehouseAttachmentType type) {
            Map<WarehouseAttachmentType, Integer> perType = sequences.computeIfAbsent(warehouseId, k -> new java.util.HashMap<>());
            Integer current = perType.get(type);
            int next = (current == null) ? 0 : current + 1;
            perType.put(type, next);
            return next;
        }
    }
}
