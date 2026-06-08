package com.xiyu.bid.personnel.application.service;

import com.xiyu.bid.exception.InvalidArgumentException;
import com.xiyu.bid.personnel.application.dto.BatchAttachmentUploadResult;
import com.xiyu.bid.personnel.application.dto.BatchAttachmentUploadResult.UnmatchedFileInfo;
import com.xiyu.bid.personnel.domain.importvalidation.AttachmentNameParser;
import com.xiyu.bid.personnel.domain.importvalidation.ParsedAttachmentName;
import com.xiyu.bid.personnel.domain.port.PersonnelFileStorage;
import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelCertificateEntity;
import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelEntity;
import com.xiyu.bid.personnel.infrastructure.persistence.repository.PersonnelCertificateJpaRepository;
import com.xiyu.bid.personnel.infrastructure.persistence.repository.PersonnelJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 批量关联证书附件应用服务。
 * 职责：编排文件名解析 → 人员查找 → 证书匹配 → 文件存储 → URL 更新。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchAttachmentAppService {

    private final PersonnelJpaRepository personnelJpaRepository;
    private final PersonnelCertificateJpaRepository certJpaRepository;
    private final PersonnelFileStorage fileStorage;

    /**
     * 批量上传并关联证书附件。
     * 对每个文件：解析文件名 → 按工号查人员 → 按证书名查证书 → 存储 → 更新 attachmentUrl。
     * 返回成功数、失败数、未匹配文件详情。
     */
    @Transactional
    public BatchAttachmentUploadResult batchUpload(List<MultipartFile> files) {
        int successCount = 0;
        List<UnmatchedFileInfo> unmatchedFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                if (processSingleFile(file)) {
                    successCount++;
                } else {
                    unmatchedFiles.add(new UnmatchedFileInfo(
                            file.getOriginalFilename(), "该人员下未找到匹配的证书"
                    ));
                }
            } catch (InvalidArgumentException e) {
                unmatchedFiles.add(new UnmatchedFileInfo(
                        file.getOriginalFilename(), e.getMessage()
                ));
            } catch (RuntimeException e) {
                log.error("批量附件上传处理失败: {}", file.getOriginalFilename(), e);
                unmatchedFiles.add(new UnmatchedFileInfo(
                        file.getOriginalFilename(), e.getMessage() != null ? e.getMessage() : "处理异常"
                ));
            }
        }

        return new BatchAttachmentUploadResult(successCount, unmatchedFiles.size(), unmatchedFiles);
    }

    private boolean processSingleFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidArgumentException("文件为空");
        }

        ParsedAttachmentName parsed = AttachmentNameParser.parse(file.getOriginalFilename());
        String empNo = parsed.employeeNumber();
        String certName = parsed.certificateNamePart();

        if (empNo == null || empNo.isBlank()) {
            throw new InvalidArgumentException("文件名无法解析工号，请遵循 PER_姓名_工号_序号_证书名.pdf 格式");
        }
        if (certName == null || certName.isBlank()) {
            throw new InvalidArgumentException("文件名无法解析证书名称，请遵循 PER_姓名_工号_序号_证书名.pdf 格式");
        }

        // 1. 按工号查找人员
        Optional<PersonnelEntity> personnelOpt = personnelJpaRepository.findByEmployeeNumber(empNo);
        if (personnelOpt.isEmpty()) {
            throw new InvalidArgumentException("未找到工号为 " + empNo + " 的人员");
        }
        PersonnelEntity personnel = personnelOpt.get();

        // 2. 按证书名称查找该人员下未软删除的证书（精确匹配）
        Optional<PersonnelCertificateEntity> certOpt = findCertificateByName(personnel.getId(), certName);
        if (certOpt.isEmpty()) {
            return false;
        }
        PersonnelCertificateEntity cert = certOpt.get();

        // 3. 存储文件
        byte[] content;
        try {
            content = file.getBytes();
        } catch (java.io.IOException e) {
            throw new RuntimeException("读取上传文件失败", e);
        }

        Integer seq = parsed.sequenceNumber() != null ? parsed.sequenceNumber() : 1;
        String url = fileStorage.storeCertAttachmentWithNaming(
                personnel.getId(),
                cert.getId(),
                content,
                personnel.getName(),
                personnel.getEmployeeNumber(),
                seq,
                certName,
                file.getOriginalFilename(),
                file.getContentType()
        );

        // 4. 更新证书附件 URL
        cert.setAttachmentUrl(url);
        certJpaRepository.save(cert);
        return true;
    }

    private Optional<PersonnelCertificateEntity> findCertificateByName(Long personnelId, String certName) {
        return certJpaRepository.findByPersonnelIdAndDeletedAtIsNull(personnelId).stream()
                .filter(c -> certName.equals(c.getCertificateName()))
                .findFirst();
    }
}
