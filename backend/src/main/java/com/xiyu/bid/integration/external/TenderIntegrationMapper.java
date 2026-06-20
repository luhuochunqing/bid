package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.tender.dto.ContactDTO;
import com.xiyu.bid.tender.dto.TenderAttachmentDTO;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.entity.TenderAttachment;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 外部标讯集成专用 Mapper。
 * 负责 Request → Entity、Entity → DTO、以及 DTO 标准化转换。
 */
@Component
@RequiredArgsConstructor
public class TenderIntegrationMapper {

    private final TenderMapper tenderMapper;
    private final TenderEvaluationIntegrationMapper evaluationMapper;

    /**
     * 将推送请求映射为 Tender 实体。
     */
    Tender toEntity(TenderPushRequest r) {
        Tender t = new Tender();
        t.setTitle(InputSanitizer.sanitizeString(r.getTitle(), 500));
        if (r.getCustomerName() != null) {
            t.setPurchaserName(InputSanitizer.sanitizeString(r.getCustomerName(), 500));
        }
        if (r.getPublishDate() != null) {
            t.setPublishDate(r.getPublishDate());
        }
        if (r.getDueDate() != null) t.setDeadline(parseDateTime(r.getDueDate()));
        if (r.getBudgetAmount() != null) {
            t.setBudget(r.getBudgetAmount());
        }
        applyBasicInfo(t, r.getRegion(), r.getIndustry(), r.getTenderAgency(),
                r.getBidOpeningTime(), r.getRegistrationDeadline(), r.getCustomerType(),
                r.getPriority(), r.getProjectType(), r.getSourcePlatform(), r.getSource(), r.getTags());
        applyContactInfo(t, r.getContactInfo());
        if (r.getContentDesc() != null) {
            t.setDescription(InputSanitizer.sanitizeString(r.getContentDesc(), 5000));
        }
        if (r.getTenderInfo() != null) t.setTenderInfo(InputSanitizer.sanitizeString(r.getTenderInfo(), 5000));
        if (r.getProjectManagerName() != null) t.setProjectManagerName(InputSanitizer.sanitizeString(r.getProjectManagerName(), 100));
        if (r.getDepartment() != null) t.setDepartment(InputSanitizer.sanitizeString(r.getDepartment(), 100));
        if (r.getCreatorName() != null) t.setCreatorName(InputSanitizer.sanitizeString(r.getCreatorName(), 100));
        if (r.getCreateDate() != null) t.setCreatedAt(parseDateTime(r.getCreateDate()));
        boolean isFromCrm = firstNonBlank(r.getCrmOpportunityId(), r.getCrmId()) != null;
        if (isFromCrm) {
            t.setSourceType(Tender.SourceType.CRM_OPPORTUNITY);
            t.setSource(Tender.SourceType.CRM_OPPORTUNITY.getLabel());
            t.setStatus(Tender.Status.EVALUATED);
        } else {
            t.setSourceType(Tender.SourceType.EXTERNAL_PLATFORM);
            t.setSource(Tender.SourceType.EXTERNAL_PLATFORM.getLabel());
            t.setStatus(Tender.Status.PENDING_ASSIGNMENT);
        }
        t.setEvaluationSource(Tender.EvaluationSource.CRM_PUSH);
        return t;
    }

    /**
     * 将 Entity 转换为 DTO（含联系人、评估、附件、来源标准化）。
     */
    TenderDTO toDTO(Tender tender, List<TenderAttachment> attachments) {
        TenderDTO dto = tenderMapper.toDTO(tender);
        dto.setContactInfo(tenderMapper.buildContacts(tender));
        dto.setEvaluation(evaluationMapper.buildEvaluationDTO(tender.getId(), tender));
        dto.setAttachments(toAttachmentDTOs(attachments));
        normalizeSourceForIntegration(dto, tender);
        normalizeFileUrls(dto);
        return dto;
    }

    /**
     * 将附件实体列表转换为 DTO 列表。
     */
    List<TenderAttachmentDTO> toAttachmentDTOs(List<TenderAttachment> attachments) {
        if (attachments == null) return List.of();
        return attachments.stream()
                .map(a -> TenderAttachmentDTO.builder()
                        .fileName(a.getFileName())
                        .fileType(a.getFileType())
                        .fileUrl(a.getFileUrl())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 将请求中的联系人数组映射到实体扁平字段（最多取前 2 个）。
     */
    void applyContactInfo(Tender tender, List<ContactDTO> contactInfo) {
        if (contactInfo == null || contactInfo.isEmpty()) {
            return;
        }
        tender.setContactName(null);
        tender.setContactPhone(null);
        tender.setContactTel(null);
        tender.setContactMail(null);
        tender.setContactName2(null);
        tender.setContactPhone2(null);
        tender.setContactTel2(null);
        tender.setContactMail2(null);
        ContactDTO c1 = contactInfo.get(0);
        if (c1.getName() != null) tender.setContactName(InputSanitizer.sanitizeString(c1.getName(), 100));
        if (c1.getPhone() != null) tender.setContactPhone(InputSanitizer.sanitizeString(c1.getPhone(), 50));
        if (c1.getTel() != null) tender.setContactTel(InputSanitizer.sanitizeString(c1.getTel(), 50));
        if (c1.getMail() != null) tender.setContactMail(InputSanitizer.sanitizeString(c1.getMail(), 100));

        if (contactInfo.size() > 1) {
            ContactDTO c2 = contactInfo.get(1);
            if (c2.getName() != null) tender.setContactName2(InputSanitizer.sanitizeString(c2.getName(), 100));
            if (c2.getPhone() != null) tender.setContactPhone2(InputSanitizer.sanitizeString(c2.getPhone(), 50));
            if (c2.getTel() != null) tender.setContactTel2(InputSanitizer.sanitizeString(c2.getTel(), 50));
            if (c2.getMail() != null) tender.setContactMail2(InputSanitizer.sanitizeString(c2.getMail(), 100));
        }
    }

    /**
     * 将请求中的基本信息字段映射到实体（非空才覆盖）。
     */
    void applyBasicInfo(Tender t, String region, String industry, String tenderAgency,
                        String bidOpeningTime, String registrationDeadline, String customerType,
                        String priority, String projectType, String sourcePlatform,
                        String source, List<String> tags) {
        if (region != null) t.setRegion(InputSanitizer.sanitizeString(region, 100));
        if (industry != null) t.setIndustry(InputSanitizer.sanitizeString(industry, 100));
        if (tenderAgency != null) t.setTenderAgency(InputSanitizer.sanitizeString(tenderAgency, 255));
        if (bidOpeningTime != null) t.setBidOpeningTime(parseDateTime(bidOpeningTime));
        if (registrationDeadline != null) t.setRegistrationDeadline(parseDateTime(registrationDeadline));
        if (customerType != null) t.setCustomerType(InputSanitizer.sanitizeString(customerType, 100));
        if (priority != null) t.setPriority(InputSanitizer.sanitizeString(priority, 10));
        if (projectType != null) t.setProjectType(InputSanitizer.sanitizeString(projectType, 20));
        if (sourcePlatform != null) t.setSourcePlatform(InputSanitizer.sanitizeString(sourcePlatform, 100));
        if (source != null) t.setSource(InputSanitizer.sanitizeString(source, 200));
        if (tags != null) t.setTags(tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse(""));
    }

    /**
     * 将更新请求中的字段应用到已有实体。
     */
    void applyUpdate(Tender tender, TenderPushRequest r) {
        if (r.getTitle() != null) tender.setTitle(InputSanitizer.sanitizeString(r.getTitle(), 500));
        if (r.getCustomerName() != null) tender.setPurchaserName(InputSanitizer.sanitizeString(r.getCustomerName(), 500));
        if (r.getPublishDate() != null) tender.setPublishDate(r.getPublishDate());
        if (r.getDueDate() != null) tender.setDeadline(parseDateTime(r.getDueDate()));
        if (r.getBudgetAmount() != null) tender.setBudget(r.getBudgetAmount());
        applyBasicInfo(tender, r.getRegion(), r.getIndustry(), r.getTenderAgency(),
                r.getBidOpeningTime(), r.getRegistrationDeadline(), r.getCustomerType(),
                r.getPriority(), r.getProjectType(), r.getSourcePlatform(), r.getSource(), r.getTags());
        applyContactInfo(tender, r.getContactInfo());
        if (r.getContentDesc() != null) tender.setDescription(InputSanitizer.sanitizeString(r.getContentDesc(), 5000));
        tender.setEvaluationSource(Tender.EvaluationSource.CRM_PUSH);
        tender.setStatus(Tender.Status.EVALUATED);
    }

    /**
     * 将 source 字段映射为中文标签，与 sourceType 保持一致。
     */
    void normalizeSourceForIntegration(TenderDTO dto, Tender tender) {
        if (tender.getSourceType() == null) return;
        switch (tender.getSourceType()) {
            case MANUAL_SINGLE:
            case BULK_IMPORT:
                dto.setSource("人工录入");
                break;
            case CRM_OPPORTUNITY:
                dto.setSource("CRM 创建");
                break;
            case EXTERNAL_PLATFORM:
                dto.setSource("第三方平台");
                break;
            default:
                break;
        }
    }

    /**
     * 将 doc-insight:// 格式的 URL 转换为可直接下载的 URL。
     */
    void normalizeFileUrls(TenderDTO dto) {
        if (dto.getSourceDocumentFileUrl() != null && dto.getSourceDocumentFileUrl().startsWith("doc-insight://")) {
            dto.setSourceDocumentFileUrl(toDownloadUrl(dto.getSourceDocumentFileUrl()));
        }
        if (dto.getBidNoticeFileUrl() != null && dto.getBidNoticeFileUrl().startsWith("doc-insight://")) {
            dto.setBidNoticeFileUrl(toDownloadUrl(dto.getBidNoticeFileUrl()));
        }
        if (dto.getAttachments() != null) {
            dto.getAttachments().forEach(a -> {
                if (a.getFileUrl() != null && a.getFileUrl().startsWith("doc-insight://")) {
                    a.setFileUrl(toDownloadUrl(a.getFileUrl()));
                }
            });
        }
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────────

    static String toDownloadUrl(String u) {
        return "/api/doc-insight/download?fileUrl=" + URLEncoder.encode(u, StandardCharsets.UTF_8);
    }

    static String buildExternalId(String sourceSystem, String sourceId) {
        return sourceSystem + ":" + sourceId;
    }

    static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.replace(' ', 'T');
        if (normalized.length() == 16) normalized += ":00";
        return LocalDateTime.parse(normalized);
    }
}
