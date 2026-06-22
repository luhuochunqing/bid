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

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 外部标讯集成专用 Mapper。
 * 负责 Request → Entity、Entity → DTO、以及 DTO 标准化转换。
 *
 * <p>URL 转换逻辑已抽到 {@link TenderAttachmentUrlResolver}，避免本类超过行数预算。
 * 详见 CO-280 403 修复。
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
        if (r.getDueDate() != null) t.setDeadline(parseDateTime("dueDate", r.getDueDate()));
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
        if (r.getCreateDate() != null) t.setCreatedAt(parseDateTime("createDate", r.getCreateDate()));
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
     *
     * <p>不带 CallerContext 的版本，保持向后兼容。
     * 内部委托到 {@link #toDTO(Tender, List, CallerContext)}。
     */
    TenderDTO toDTO(Tender tender, List<TenderAttachment> attachments) {
        return toDTO(tender, attachments, CallerContext.externalSystem(null));
    }

    /**
     * 将 Entity 转换为 DTO（含联系人、评估、附件、来源标准化）。
     *
     * <p>根据 CallerContext 选择正确的端点和认证方式，标准化附件 URL。
     */
    TenderDTO toDTO(Tender tender, List<TenderAttachment> attachments, CallerContext context) {
        TenderDTO dto = tenderMapper.toDTO(tender);
        dto.setContactInfo(tenderMapper.buildContacts(tender));
        dto.setEvaluation(evaluationMapper.buildEvaluationDTO(tender.getId(), tender));
        dto.setAttachments(toAttachmentDTOs(attachments));
        normalizeSourceForIntegration(dto, tender);
        normalizeFileUrls(dto, context);
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
        if (bidOpeningTime != null) t.setBidOpeningTime(parseDateTime("bidOpeningTime", bidOpeningTime));
        if (registrationDeadline != null) t.setRegistrationDeadline(parseDateTime("registrationDeadline", registrationDeadline));
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
        if (r.getDueDate() != null) tender.setDeadline(parseDateTime("dueDate", r.getDueDate()));
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
                dto.setSource(Tender.SourceType.MANUAL_SINGLE.getLabel());
                break;
            case CRM_OPPORTUNITY:
            case EXTERNAL_PLATFORM:
                dto.setSource(tender.getSourceType().getLabel());
                break;
            default:
                break;
        }
    }

    /**
     * 将 URL 标准化为集成下载端点但不附加 api_key（CO-280 修复）。
     *
     * <p>内部委托到 {@link #normalizeFileUrls(TenderDTO, CallerContext)}，
     * 以 {@code CallerContext.externalSystem(null)} 为上下文，保持向后兼容。
     */
    void normalizeFileUrls(TenderDTO dto) {
        normalizeFileUrls(dto, CallerContext.externalSystem(null));
    }

    /**
     * 将 URL 标准化为集成下载端点并附加 api_key 查询参数（CO-280 修复）。
     *
     * <p>内部委托到 {@link #normalizeFileUrls(TenderDTO, CallerContext)}，
     * 以 {@code CallerContext.externalSystem(apiKey)} 为上下文，保持向后兼容。
     */
    void normalizeFileUrls(TenderDTO dto, String apiKey) {
        normalizeFileUrls(dto, CallerContext.externalSystem(apiKey));
    }

    /**
     * 统一入口：根据调用方上下文选择正确的端点和认证方式，标准化附件 URL。
     *
     * <p>底层使用 {@link TenderAttachmentUrlResolver#resolve(String, CallerContext)}。
     */
    void normalizeFileUrls(TenderDTO dto, CallerContext context) {
        dto.setSourceDocumentFileUrl(
                TenderAttachmentUrlResolver.resolve(dto.getSourceDocumentFileUrl(), context));
        dto.setBidNoticeFileUrl(
                TenderAttachmentUrlResolver.resolve(dto.getBidNoticeFileUrl(), context));
        if (dto.getAttachments() != null) {
            dto.getAttachments().forEach(a ->
                    a.setFileUrl(TenderAttachmentUrlResolver.resolve(a.getFileUrl(), context)));
        }
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────────

    /**
     * 构造附件下载 URL（XiYu 内部端点）。
     * 委托到 {@link TenderAttachmentUrlResolver#toDownloadUrl(String)}。
     */
    public static String toDownloadUrl(String u) {
        return TenderAttachmentUrlResolver.toDownloadUrl(u);
    }

    static String buildExternalId(String sourceSystem, String sourceId) {
        return sourceSystem + ":" + sourceId;
    }

    /**
     * 判断是否携带有效外部来源（非空且非 "_" 占位符）。
     * 用于 Resolver 的交叉校验和 externalId 查询，保证两处口径一致。
     */
    static boolean hasExternalSource(String sourceSystem, String sourceId) {
        return sourceSystem != null && !sourceSystem.isBlank() && !"_".equals(sourceSystem)
                && sourceId != null && !sourceId.isBlank() && !"_".equals(sourceId);
    }

    static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    static LocalDateTime parseDateTime(String fieldName, String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.replace(' ', 'T');
        if (normalized.length() == 16) normalized += ":00";
        try {
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(fieldName + " 格式错误，应为 yyyy-MM-ddTHH:mm 或 yyyy-MM-ddTHH:mm:ss", ex);
        }
    }
}
