package com.xiyu.bid.qualification.service;

import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.qualification.dto.BatchAttachResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * §4.2.1.4 批量关联附件服务
 *
 * 职责：
 *  1. 解析文件名提取证书编号（QUAL_{证书编号}_{序号}_{文件名}.{扩展名}）
 *  2. 按证书编号匹配证书记录
 *  3. 匹配成功则更新证书附件关联
 *  4. 同名同编号重复上传时覆盖旧文件
 *  5. 支持 .zip 压缩包：自动解压后逐文件匹配
 *  6. 返回成功关联列表 + 未匹配文件列表
 */
@Service
@RequiredArgsConstructor
public class BatchAttachmentService {

    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("^QUAL_([^_]+)_(\\d+)_(.+)\\.(.+)$");

    private final BusinessQualificationJpaRepository repository;

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
            if (file == null || file.isEmpty()) continue;

            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) {
                unmatched.add(BatchAttachResultDTO.UnmatchedItem.builder()
                        .fileName("(无名文件)").reason("文件名缺失").build());
                continue;
            }

            if (isZipFile(originalName)) {
                processZipFile(file, matched, unmatched);
            } else {
                processEntry(originalName, matched, unmatched);
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
                if (entry.isDirectory()) { zis.closeEntry(); continue; }
                String entryName = entry.getName();
                int lastSlash = entryName.lastIndexOf('/');
                String fileName = lastSlash >= 0 ? entryName.substring(lastSlash + 1) : entryName;
                processEntry(fileName, matched, unmatched);
                zis.closeEntry();
            }
        } catch (IOException e) {
            unmatched.add(BatchAttachResultDTO.UnmatchedItem.builder()
                    .fileName(file.getOriginalFilename())
                    .reason("压缩包解析失败: " + e.getMessage()).build());
        }
    }

    private void processEntry(
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

        String certificateNo = matcher.group(1);
        BusinessQualificationEntity entity = repository.findByCertificateNo(certificateNo).orElse(null);
        if (entity == null) {
            unmatched.add(BatchAttachResultDTO.UnmatchedItem.builder()
                    .fileName(originalName).reason("证书编号不存在").build());
            return;
        }

        entity.setFileUrl(originalName);
        repository.save(entity);

        matched.add(BatchAttachResultDTO.MatchedItem.builder()
                .fileName(originalName).certificateNo(certificateNo)
                .qualificationId(entity.getId()).qualificationName(entity.getName()).build());
    }
}
