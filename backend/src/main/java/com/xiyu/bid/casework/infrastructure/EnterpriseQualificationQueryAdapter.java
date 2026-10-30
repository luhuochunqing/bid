package com.xiyu.bid.casework.infrastructure;

import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.QualificationAttachmentEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.QualificationAttachmentJpaRepository;
import com.xiyu.bid.casework.application.port.EnterpriseQualificationQueryPort;
import com.xiyu.bid.casework.domain.policy.EnterpriseQualificationMatchPolicy;
import com.xiyu.bid.casework.dto.ScoreItemCaseRecommendResponse.CertificateItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 企业资质证书查询端口的基础设施适配器。
 * 桥接 business_qualifications 与 qualification_attachments，直连企业真实有效证书原件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnterpriseQualificationQueryAdapter implements EnterpriseQualificationQueryPort {

    private final BusinessQualificationJpaRepository qualificationRepository;
    private final QualificationAttachmentJpaRepository attachmentRepository;

    @Override
    public List<CertificateItem> findMatchingCertificates(String dim, String detail, String keyword) {
        List<BusinessQualificationEntity> allEntities = qualificationRepository.findAll();
        if (allEntities.isEmpty()) {
            return List.of();
        }

        // 收集资质 ID 列表批量拉取附件
        List<Long> qualIds = allEntities.stream().map(BusinessQualificationEntity::getId).toList();
        Map<Long, List<QualificationAttachmentEntity>> attachmentsByQualId = attachmentRepository.findAll().stream()
                .filter(a -> qualIds.contains(a.getQualificationId()))
                .collect(Collectors.groupingBy(QualificationAttachmentEntity::getQualificationId));

        List<EnterpriseQualificationMatchPolicy.Candidate> candidates = allEntities.stream()
                .map(entity -> {
                    String fileUrl = entity.getFileUrl();
                    String fileName = null;
                    if (fileUrl == null || fileUrl.isBlank()) {
                        List<QualificationAttachmentEntity> attaches = attachmentsByQualId.get(entity.getId());
                        if (attaches != null && !attaches.isEmpty()) {
                            fileUrl = attaches.get(0).getFileUrl();
                            fileName = attaches.get(0).getFileName();
                        }
                    }
                    return new EnterpriseQualificationMatchPolicy.Candidate(
                            entity.getId(),
                            entity.getName(),
                            entity.getLevel(),
                            entity.getCertificateNo(),
                            entity.getIssuer(),
                            entity.getIssueDate(),
                            entity.getExpiryDate(),
                            entity.getStatus() != null ? entity.getStatus().name() : null,
                            entity.getCertScope(),
                            fileUrl,
                            fileName,
                            entity.isRetired()
                    );
                })
                .toList();

        List<EnterpriseQualificationMatchPolicy.MatchResult> matched =
                EnterpriseQualificationMatchPolicy.matchCertificates(dim, detail, keyword, candidates, LocalDate.now());

        log.info("企业资质证书匹配：总候选 {} 条，针对 [{}] 命中 {} 项证书",
                candidates.size(), dim, matched.size());

        return matched.stream()
                .map(m -> CertificateItem.builder()
                        .id(m.id())
                        .name(m.name())
                        .level(m.level())
                        .certificateNo(m.certificateNo())
                        .issuer(m.issuer())
                        .issueDate(m.issueDate())
                        .expiryDate(m.expiryDate())
                        .status(m.status())
                        .certScope(m.certScope())
                        .fileUrl(m.fileUrl())
                        .fileName(m.fileName())
                        .matchScore(m.matchScore())
                        .matchLabel(m.matchLabel())
                        .matchReasons(m.matchReasons())
                        .build())
                .toList();
    }
}
