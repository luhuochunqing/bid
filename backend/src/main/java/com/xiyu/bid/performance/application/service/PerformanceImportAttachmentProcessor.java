package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceAttachmentEntity;
import com.xiyu.bid.performance.infrastructure.persistence.repository.PerformanceAttachmentJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业绩批量导入附件归档器：
 * 将用户上传的附件包按 Excel 中填写的文件名匹配到对应业绩记录，落盘并写入附件表。
 * 匹配规则：附件包中文件的 originalFilename 与 Excel 中填写的附件文件名完全一致（忽略大小写、首尾空格）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceImportAttachmentProcessor {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final PerformanceAttachmentJpaRepository attachmentRepo;

    @Value("${performance.attachment.root:data/attachments/performance}")
    private String attachmentRoot;

    @Transactional
    public AttachmentResult attachFiles(List<PerformanceRowImporter.ImportRowResult> importedRows,
                                        List<AttachmentInput> attachments) {
        if (attachments == null || attachments.isEmpty()) return new AttachmentResult(0, List.of());

        // 构建 fileName → (performanceId, fileType) 映射（忽略大小写）
        Map<String, List<Binding>> bindingByName = buildBindingMap(importedRows);
        int saved = 0;
        List<UnmatchedFile> unmatched = new ArrayList<>();

        for (AttachmentInput input : attachments) {
            if (input == null || input.isEmpty()) continue;
            String origName = sanitizeFilename(input.filename());
            if (origName == null || origName.isBlank()) continue;

            String key = normalizeKey(origName);
            List<Binding> bindings = bindingByName.get(key);
            if (bindings == null || bindings.isEmpty()) {
                unmatched.add(new UnmatchedFile(origName, "Excel 中未找到对应附件文件名"));
                continue;
            }

            // 取第一个未使用的绑定
            Binding binding = bindings.stream().filter(b -> !b.used).findFirst().orElse(null);
            if (binding == null) {
                unmatched.add(new UnmatchedFile(origName, "同名附件槽位已用完"));
                continue;
            }
            binding.used = true;

            try {
                saveOneAttachment(binding.performanceId, input.bytes(), origName, binding.fileType);
                saved++;
            } catch (IOException e) {
                log.warn("附件归档失败: filename={}, error={}", origName, e.getMessage());
                unmatched.add(new UnmatchedFile(origName, "保存失败: " + e.getMessage()));
            }
        }
        return new AttachmentResult(saved, unmatched);
    }

    private Map<String, List<Binding>> buildBindingMap(List<PerformanceRowImporter.ImportRowResult> rows) {
        Map<String, List<Binding>> map = new HashMap<>();
        for (var row : rows) {
            if (row.attachmentFileNames() == null) continue;
            for (var fn : row.attachmentFileNames()) {
                String key = normalizeKey(fn.fileName());
                map.computeIfAbsent(key, k -> new ArrayList<>())
                   .add(new Binding(row.performanceId(), fn.fileType()));
            }
        }
        return map;
    }

    private static String normalizeKey(String fileName) {
        if (fileName == null) return "";
        return fileName.trim().toLowerCase();
    }

    /**
     * 取纯文件名：去除 multipart 可能携带的 Windows/Unix 路径前缀。
     * 参考仓库模块 WarehouseFileService.sanitizeDisplayName 实现。
     */
    private static String sanitizeFilename(String fileName) {
        if (fileName == null) return null;
        int lastSep = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return lastSep >= 0 ? fileName.substring(lastSep + 1) : fileName;
    }

    private void saveOneAttachment(Long performanceId, byte[] bytes, String origName,
                                    String fileType) throws IOException {
        String ext = extractExt(origName);
        String storedFilename = String.format("PF_%d_%s_%s.%s",
                performanceId, fileType, LocalDateTime.now().format(TS_FMT), ext);
        Path dir = Paths.get(attachmentRoot, String.valueOf(performanceId));
        Files.createDirectories(dir);
        Path target = dir.resolve(storedFilename);
        Files.write(target, bytes);

        // 更新对应附件记录的 fileUrl（fileName + fileType 匹配）
        List<PerformanceAttachmentEntity> existing = attachmentRepo.findByPerformanceId(performanceId);
        String fileUrl = "/" + performanceId + "/" + storedFilename;
        var match = existing.stream()
                .filter(a -> fileType.equals(a.getFileType())
                        && origName.trim().equalsIgnoreCase(a.getFileName())
                        && (a.getFileUrl() == null || a.getFileUrl().isEmpty()))
                .findFirst();
        if (match.isPresent()) {
            match.get().setFileUrl(fileUrl);
            attachmentRepo.save(match.get());
        } else {
            // 没有匹配的预记录（可能 Excel 中没填该文件名），创建一条新记录
            PerformanceAttachmentEntity entity = PerformanceAttachmentEntity.builder()
                    .performanceId(performanceId)
                    .fileName(origName)
                    .fileUrl(fileUrl)
                    .fileType(fileType)
                    .build();
            attachmentRepo.save(entity);
        }
    }

    private static String extractExt(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1) ? filename.substring(dot + 1).toLowerCase() : "bin";
    }

    private static class Binding {
        final Long performanceId;
        final String fileType;
        boolean used = false;

        Binding(Long performanceId, String fileType) {
            this.performanceId = performanceId;
            this.fileType = fileType;
        }
    }

    public record AttachmentInput(String filename, byte[] bytes) {
        public boolean isEmpty() {
            return bytes == null || bytes.length == 0;
        }
    }

    public record AttachmentResult(int matchedCount, List<UnmatchedFile> unmatched) {}
    public record UnmatchedFile(String filename, String reason) {}

    /**
     * 检查 Excel 中声明的附件在附件包中是否都有对应文件。
     * 返回缺失的声明附件列表（按槽位顺序，先声明先占用）。
     */
    public List<MissingAttachment> findMissingDeclaredAttachments(
            List<PerformanceRowImporter.ParsedRow> rows,
            List<AttachmentInput> attachments) {
        List<MissingAttachment> declared = new ArrayList<>();
        for (var row : rows) {
            if (row.attachmentFileNames() == null) {
                continue;
            }
            for (var fn : row.attachmentFileNames()) {
                declared.add(new MissingAttachment(
                        row.rowNum(), row.contractName(), fn.fileName(), fn.fileType()));
            }
        }
        if (declared.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> availableByKey = new HashMap<>();
        if (attachments != null) {
            for (var input : attachments) {
                if (input == null || input.isEmpty()) {
                    continue;
                }
                String key = normalizeKey(input.filename());
                availableByKey.merge(key, 1, Integer::sum);
            }
        }

        List<MissingAttachment> missing = new ArrayList<>();
        for (var d : declared) {
            String key = normalizeKey(d.fileName());
            int available = availableByKey.getOrDefault(key, 0);
            if (available <= 0) {
                missing.add(d);
            } else {
                availableByKey.put(key, available - 1);
            }
        }
        return missing;
    }

    public record MissingAttachment(int rowNum, String contractName, String fileName, String fileType) {}
}
