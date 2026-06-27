package com.xiyu.bid.qualification.service;

import com.xiyu.bid.businessqualification.domain.port.QualificationFileStorage;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.QualificationAttachmentEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.QualificationAttachmentJpaRepository;
import com.xiyu.bid.qualification.dto.BatchAttachResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * §4.2.1.4 批量关联附件服务。
 * 职责：
 *  1. 解析文件名提取证书编号（QUAL_{证书编号}_{序号}_{文件名}.{扩展名}）
 *  2. 按证书编号匹配证书记录
 *  3. 读取文件字节 → 存储到磁盘 → 更新 fileUrl 为可下载 URL
 *  4. 同名同编号重复上传时覆盖旧文件
 *  5. 支持 .zip 压缩包：自动解压后逐文件处理
 *  6. 返回成功关联列表 + 未匹配文件列表
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchAttachmentService {

    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("^QUAL_([^_]+)_(\\d+)_(.+)\\.(.+)$");

    private final BusinessQualificationJpaRepository repository;
    private final QualificationFileStorage fileStorage;
    private final QualificationAttachmentJpaRepository attachmentRepository;

    @Transactional
    public BatchAttachResultDTO process(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return BatchAttachResultDTO.builder()
                    .total(0).success(0).failed(0)
                    .matched(List.of()).unmatched(List.of())
                    .build();
        }

        List<BatchAttachResultDTO.MatchedItem> matched = new ArrayList<>();
        List<BatchAttachResultDTO.UnmatchedItem> unmatched = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                unmatched.add(BatchAttachResultDTO.UnmatchedItem.builder()
                        .fileName("(空文件)").reason("文件为空").build());
                continue;
            }

            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) {
                unmatched.add(BatchAttachResultDTO.UnmatchedItem.builder()
                        .fileName("(无名文件)").reason("文件名缺失").build());
                continue;
            }

            try {
                if (isZipFile(originalName)) {
                    processZipFile(file, matched, unmatched);
                } else {
                    processEntry(file, originalName, matched, unmatched);
                }
            } catch (RuntimeException e) {
                log.error("批量附件上传处理失败: {}", originalName, e);
                unmatched.add(BatchAttachResultDTO.UnmatchedItem.builder()
                        .fileName(originalName)
                        .reason(e.getMessage() != null ? e.getMessage() : "处理异常").build());
            }
        }

        return BatchAttachResultDTO.builder()
                .total(matched.size() + unmatched.size())
                .success(matched.size()).failed(unmatched.size())
                .matched(matched).unmatched(unmatched)
                .build();
    }

    private boolean isZipFile(String filename) {
        return filename.toLowerCase().endsWith(".zip");
    }

    private void processZipFile(
            MultipartFile file,
            List<BatchAttachResultDTO.MatchedItem> matched,
            List<BatchAttachResultDTO.UnmatchedItem> unmatched
    ) {
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String entryName = entry.getName();
                int lastSlash = entryName.lastIndexOf('/');
                String fileName = lastSlash >= 0 ? entryName.substring(lastSlash + 1) : entryName;

                byte[] content = readAllBytes(zis);
                InMemoryMultipartFile innerFile = new InMemoryMultipartFile(fileName, content);
                processEntry(innerFile, fileName, matched, unmatched);
                zis.closeEntry();
            }
        } catch (IOException e) {
            unmatched.add(BatchAttachResultDTO.UnmatchedItem.builder()
                    .fileName(file.getOriginalFilename())
                    .reason("压缩包解析失败: " + e.getMessage()).build());
        }
    }

    private void processEntry(
            MultipartFile file,
            String originalName,
            List<BatchAttachResultDTO.MatchedItem> matched,
            List<BatchAttachResultDTO.UnmatchedItem> unmatched
    ) {
        Matcher matcher = FILE_NAME_PATTERN.matcher(originalName);
        if (!matcher.matches()) {
            unmatched.add(BatchAttachResultDTO.UnmatchedItem.builder()
                    .fileName(originalName)
                    .reason("命名格式不符（应 QUAL_{证书编号}_{序号}_{文件名}.{扩展名}）").build());
            return;
        }

        String certificateNo = matcher.group(1).trim();
        int sequence;
        try {
            sequence = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException e) {
            sequence = 1;
        }

        List<BusinessQualificationEntity> entities = repository.findAllByCertificateNo(certificateNo);
        BusinessQualificationEntity entity = entities.stream()
                .max(Comparator.comparingLong(BusinessQualificationEntity::getId))
                .orElse(null);
        if (entity == null) {
            unmatched.add(BatchAttachResultDTO.UnmatchedItem.builder()
                    .fileName(originalName).reason("证书编号不存在").build());
            return;
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            unmatched.add(BatchAttachResultDTO.UnmatchedItem.builder()
                    .fileName(originalName).reason("读取文件失败: " + e.getMessage()).build());
            return;
        }

        if (content.length == 0) {
            unmatched.add(BatchAttachResultDTO.UnmatchedItem.builder()
                    .fileName(originalName).reason("文件内容为空").build());
            return;
        }

        String storedFileName = fileStorage.storeAttachmentWithNaming(
                entity.getId(),
                content,
                certificateNo,
                sequence,
                entity.getName(),
                originalName,
                file.getContentType()
        );

        entity.setFileUrl(storedFileName);
        repository.save(entity);

        QualificationAttachmentEntity attachment = QualificationAttachmentEntity.builder()
                .qualificationId(entity.getId())
                .fileName(originalName)
                .fileUrl(storedFileName)
                .uploadedAt(LocalDateTime.now())
                .build();
        attachmentRepository.save(attachment);

        matched.add(BatchAttachResultDTO.MatchedItem.builder()
                .fileName(originalName).certificateNo(certificateNo)
                .qualificationId(entity.getId()).qualificationName(entity.getName()).build());
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }

    /**
     * 内存中的 MultipartFile，用于承载 zip 解压后的单个文件。
     */
    @lombok.RequiredArgsConstructor
    private static class InMemoryMultipartFile implements MultipartFile {
        private final String fileName;
        private final byte[] content;

        @Override public String getName() { return fileName; }
        @Override public String getOriginalFilename() { return fileName; }
        @Override public String getContentType() { return null; }
        @Override public boolean isEmpty() { return content == null || content.length == 0; }
        @Override public long getSize() { return content != null ? content.length : 0; }
        @Override public byte[] getBytes() { return content; }
        @Override public InputStream getInputStream() { return new java.io.ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
