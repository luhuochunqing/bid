package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.integration.external.TenderAttachmentUrlResolver;
import com.xiyu.bid.integration.external.TenderIntegrationMapper;
import com.xiyu.bid.tender.entity.TenderAttachment;
import com.xiyu.bid.tender.dto.ContactDTO;
import com.xiyu.bid.tender.dto.TenderAttachmentDTO;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.dto.TenderRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TenderMapper {

    private static final int BID_NOTICE_TRUNCATE = 200;
    public TenderDTO toDTO(Tender tender) {
        if (tender == null) {
            return null;
        }
        return TenderDTO.builder()
                .id(tender.getId())
                .title(tender.getTitle())
                .source(tender.getSource())
                .budget(tender.getBudget())
                .region(tender.getRegion())
                .industry(tender.getIndustry())
                .tenderAgency(tender.getTenderAgency())
                .purchaserName(tender.getPurchaserName())
                .purchaserHash(tender.getPurchaserHash())
                .publishDate(tender.getPublishDate())
                .deadline(tender.getDeadline())
                .bidOpeningTime(tender.getBidOpeningTime())
                .registrationDeadline(tender.getRegistrationDeadline())
                .contactName(tender.getContactName())
                .contactPhone(tender.getContactPhone())
                .contactTel(tender.getContactTel())
                .contactMail(tender.getContactMail())
                .contactName2(tender.getContactName2())
                .contactPhone2(tender.getContactPhone2())
                .contactTel2(tender.getContactTel2())
                .contactMail2(tender.getContactMail2())
                .contactInfo(buildContacts(tender))
                .sourceDocumentName(tender.getSourceDocumentName())
                .sourceDocumentFileType(tender.getSourceDocumentFileType())
                .sourceDocumentFileUrl(TenderAttachmentUrlResolver.toDownloadUrl(tender.getSourceDocumentFileUrl()))
                .attachments(tender.getAttachments() == null || tender.getAttachments().isEmpty() ? Collections.emptyList()
                        : tender.getAttachments().stream().map(a -> TenderAttachmentDTO.builder()
                                .fileName(a.getFileName()).fileType(a.getFileType()).fileUrl(toDownloadUrl(a.getFileUrl())).build())
                                .collect(Collectors.toList()))
                .customerType(tender.getCustomerType())
                .priority(tender.getPriority())
                .description(tender.getDescription())
                .tags(decodeTags(tender.getTags()))
                .status(tender.getStatus())
                .aiScore(tender.getAiScore())
                .riskLevel(tender.getRiskLevel())
                .sourceType(tender.getSourceType())
                .originalUrl(tender.getOriginalUrl())
                .externalId(tender.getExternalId())
                .projectManagerId(tender.getProjectManagerId())
                .projectManagerName(tender.getProjectManagerName())
                .biddingPersonId(tender.getBiddingPersonId())
                .biddingPersonName(tender.getBiddingPersonName())
                .department(tender.getDepartment())
                .distributorId(tender.getDistributorId())
                .distributorName(tender.getDistributorName())
                .creatorId(tender.getCreatorId())
                .creatorName(tender.getCreatorName())
                .bidNotice(truncate(tender.getBidNotice(), BID_NOTICE_TRUNCATE))
                .bidNoticeFileUrl(TenderAttachmentUrlResolver.toDownloadUrl(tender.getBidNoticeFileUrl()))
                .projectType(tender.getProjectType())
                .projectId(tender.getProjectId())
                .tenderInfo(tender.getTenderInfo())
                .sourcePlatform(tender.getSourcePlatform())
                .crmOpportunityId(tender.getCrmOpportunityId())
                .crmOpportunityName(tender.getCrmOpportunityName())
                .evaluationSource(tender.getEvaluationSource() != null ? tender.getEvaluationSource().name() : null)
                .abandonmentReason(tender.getAbandonmentReason())
                .createdAt(tender.getCreatedAt())
                .updatedAt(tender.getUpdatedAt())
                .basicInfoSavedAt(tender.getBasicInfoSavedAt())
                .assigneeName(null)
                .build();
    }

    public TenderDTO toDTO(TenderRequest request) {
        if (request == null) {
            return null;
        }
        return TenderDTO.builder()
                .title(request.getTitle())
                .source(request.getSource())
                .budget(request.getBudget())
                .region(request.getRegion())
                .industry(request.getIndustry())
                .tenderAgency(request.getTenderAgency())
                .purchaserName(request.getPurchaserName())
                .purchaserHash(request.getPurchaserHash())
                .publishDate(request.getPublishDate())
                .deadline(request.getDeadline())
                .bidOpeningTime(request.getBidOpeningTime())
                .registrationDeadline(request.getRegistrationDeadline())
                .contactName(request.getContactName())
                .contactPhone(request.getContactPhone())
                .contactTel(request.getContactTel())
                .contactMail(request.getContactMail())
                .contactName2(request.getContactName2())
                .contactPhone2(request.getContactPhone2())
                .contactTel2(request.getContactTel2())
                .contactMail2(request.getContactMail2())
                .projectType(request.getProjectType())
                .sourceDocumentName(request.getSourceDocumentName())
                .sourceDocumentFileType(request.getSourceDocumentFileType())
                .sourceDocumentFileUrl(request.getSourceDocumentFileUrl())
                .attachments(request.getAttachments())
                .customerType(request.getCustomerType())
                .priority(request.getPriority())
                .description(request.getDescription())
                .tags(request.getTags())
                .status(request.getStatus())
                .aiScore(request.getAiScore())
                .riskLevel(request.getRiskLevel())
                .sourceType(request.getSourceType())
                .originalUrl(request.getOriginalUrl())
                .externalId(request.getExternalId())
                .tenderInfo(request.getTenderInfo())
                .sourcePlatform(request.getSourcePlatform())
                .crmOpportunityId(request.getCrmOpportunityId())
                .crmOpportunityName(request.getCrmOpportunityName())
                .build();
    }

    public Tender toEntity(TenderDTO dto) {
        if (dto == null) {
            return null;
        }
        Tender.TenderBuilder builder = Tender.builder()
                .id(dto.getId())
                .title(dto.getTitle())
                .source(dto.getSource())
                .budget(dto.getBudget())
                .region(dto.getRegion())
                .industry(dto.getIndustry())
                .tenderAgency(dto.getTenderAgency())
                .purchaserName(dto.getPurchaserName())
                .purchaserHash(dto.getPurchaserHash())
                .publishDate(dto.getPublishDate())
                .deadline(dto.getDeadline())
                .bidOpeningTime(dto.getBidOpeningTime())
                .registrationDeadline(dto.getRegistrationDeadline())
                .contactName(dto.getContactName())
                .contactPhone(dto.getContactPhone())
                .contactTel(dto.getContactTel())
                .contactMail(dto.getContactMail())
                .contactName2(dto.getContactName2())
                .contactPhone2(dto.getContactPhone2())
                .contactTel2(dto.getContactTel2())
                .contactMail2(dto.getContactMail2())
                .customerType(dto.getCustomerType())
                .priority(dto.getPriority())
                .description(dto.getDescription())
                .tags(encodeTags(dto.getTags()))
                .aiScore(dto.getAiScore())
                .riskLevel(dto.getRiskLevel())
                .sourceType(dto.getSourceType())
                .originalUrl(dto.getOriginalUrl())
                .externalId(dto.getExternalId())
                .projectManagerId(dto.getProjectManagerId())
                .projectManagerName(dto.getProjectManagerName())
                .biddingPersonId(dto.getBiddingPersonId())
                .biddingPersonName(dto.getBiddingPersonName())
                .department(dto.getDepartment())
                .distributorId(dto.getDistributorId())
                .distributorName(dto.getDistributorName())
                .creatorId(dto.getCreatorId())
                .creatorName(dto.getCreatorName())
                .bidNotice(dto.getBidNotice())
                .bidNoticeFileUrl(dto.getBidNoticeFileUrl())
                .projectType(dto.getProjectType())
                .tenderInfo(dto.getTenderInfo())
                .sourcePlatform(dto.getSourcePlatform())
                .crmOpportunityId(dto.getCrmOpportunityId())
                .crmOpportunityName(dto.getCrmOpportunityName())
                .evaluationSource(dto.getEvaluationSource() != null ? Tender.EvaluationSource.valueOf(dto.getEvaluationSource()) : null)
                .abandonmentReason(dto.getAbandonmentReason())
                .basicInfoSavedAt(dto.getBasicInfoSavedAt());
        if (dto.getStatus() != null) {
            builder.status(dto.getStatus());
        }
        return TenderAttachmentToSourceDocumentMapper.apply(builder.build(), dto);
    }

    public void updateEntity(Tender target, TenderDTO dto) {
        if (target == null || dto == null) return;
        if (dto.getTitle() != null) target.setTitle(dto.getTitle());
        if (dto.getSource() != null) target.setSource(dto.getSource());
        if (dto.getBudget() != null) target.setBudget(dto.getBudget());
        if (dto.getRegion() != null) target.setRegion(dto.getRegion());
        if (dto.getIndustry() != null) target.setIndustry(dto.getIndustry());
        if (dto.getTenderAgency() != null) target.setTenderAgency(dto.getTenderAgency());
        if (dto.getPurchaserName() != null) target.setPurchaserName(dto.getPurchaserName());
        if (dto.getPurchaserHash() != null) target.setPurchaserHash(dto.getPurchaserHash());
        if (dto.getPublishDate() != null) target.setPublishDate(dto.getPublishDate());
        if (dto.getDeadline() != null) target.setDeadline(dto.getDeadline());
        if (dto.getBidOpeningTime() != null) target.setBidOpeningTime(dto.getBidOpeningTime());
        if (dto.getRegistrationDeadline() != null) target.setRegistrationDeadline(dto.getRegistrationDeadline());
        if (dto.getContactName() != null) target.setContactName(dto.getContactName());
        if (dto.getContactPhone() != null) target.setContactPhone(dto.getContactPhone());
        if (dto.getContactTel() != null) target.setContactTel(dto.getContactTel());
        if (dto.getContactMail() != null) target.setContactMail(dto.getContactMail());
        if (dto.getContactName2() != null) target.setContactName2(dto.getContactName2());
        if (dto.getContactPhone2() != null) target.setContactPhone2(dto.getContactPhone2());
        if (dto.getContactTel2() != null) target.setContactTel2(dto.getContactTel2());
        if (dto.getContactMail2() != null) target.setContactMail2(dto.getContactMail2());
        if (dto.getCustomerType() != null) target.setCustomerType(dto.getCustomerType());
        if (dto.getPriority() != null) target.setPriority(dto.getPriority());
        if (dto.getDescription() != null) target.setDescription(dto.getDescription());
        if (dto.getTags() != null) target.setTags(encodeTags(dto.getTags()));
        if (dto.getStatus() != null) target.setStatus(dto.getStatus());
        if (dto.getAiScore() != null) target.setAiScore(dto.getAiScore());
        if (dto.getRiskLevel() != null) target.setRiskLevel(dto.getRiskLevel());
        if (dto.getSourceType() != null) target.setSourceType(dto.getSourceType());
        if (dto.getOriginalUrl() != null) target.setOriginalUrl(dto.getOriginalUrl());
        if (dto.getExternalId() != null) target.setExternalId(dto.getExternalId());
        if (dto.getProjectManagerId() != null) target.setProjectManagerId(dto.getProjectManagerId());
        if (dto.getProjectManagerName() != null) target.setProjectManagerName(dto.getProjectManagerName());
        if (dto.getBiddingPersonId() != null) target.setBiddingPersonId(dto.getBiddingPersonId());
        if (dto.getBiddingPersonName() != null) target.setBiddingPersonName(dto.getBiddingPersonName());
        if (dto.getDepartment() != null) target.setDepartment(dto.getDepartment());
        if (dto.getDistributorId() != null) target.setDistributorId(dto.getDistributorId());
        if (dto.getDistributorName() != null) target.setDistributorName(dto.getDistributorName());
        if (dto.getCreatorId() != null) target.setCreatorId(dto.getCreatorId());
        if (dto.getCreatorName() != null) target.setCreatorName(dto.getCreatorName());
        if (dto.getBidNotice() != null) target.setBidNotice(dto.getBidNotice());
        if (dto.getBidNoticeFileUrl() != null) target.setBidNoticeFileUrl(dto.getBidNoticeFileUrl());
        if (dto.getProjectType() != null) target.setProjectType(dto.getProjectType());
        if (dto.getTenderInfo() != null) target.setTenderInfo(dto.getTenderInfo());
        if (dto.getSourcePlatform() != null) target.setSourcePlatform(dto.getSourcePlatform());
        if (dto.getCrmOpportunityId() != null) target.setCrmOpportunityId(dto.getCrmOpportunityId());
        if (dto.getCrmOpportunityName() != null) target.setCrmOpportunityName(dto.getCrmOpportunityName());
        if (dto.getAbandonmentReason() != null) target.setAbandonmentReason(dto.getAbandonmentReason());
        if (dto.getBasicInfoSavedAt() != null) target.setBasicInfoSavedAt(dto.getBasicInfoSavedAt());
        TenderAttachmentToSourceDocumentMapper.apply(target, dto);
    }

    public List<ContactDTO> buildContacts(Tender tender) {
        return buildContacts(tender.getContactName(), tender.getContactPhone(), tender.getContactTel(), tender.getContactMail(),
                tender.getContactName2(), tender.getContactPhone2(), tender.getContactTel2(), tender.getContactMail2());
    }

    /** 从 DTO 扁平字段构建联系人数组（列表接口使用）。 */
    public List<ContactDTO> buildContactsFromDTO(TenderDTO dto) {
        return buildContacts(dto.getContactName(), dto.getContactPhone(), dto.getContactTel(), dto.getContactMail(),
                dto.getContactName2(), dto.getContactPhone2(), dto.getContactTel2(), dto.getContactMail2());
    }

    private List<ContactDTO> buildContacts(String n1, String p1, String t1, String m1, String n2, String p2, String t2, String m2) {
        List<ContactDTO> contacts = new ArrayList<>();
        if (n1 != null && !n1.isBlank()) contacts.add(ContactDTO.builder().name(n1).phone(p1).tel(t1).mail(m1).build());
        if (n2 != null && !n2.isBlank()) contacts.add(ContactDTO.builder().name(n2).phone(p2).tel(t2).mail(m2).build());
        return contacts;
    }

    private List<String> decodeTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .toList();
    }

    private String encodeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }
    private static String truncate(String value, int maxLen) {
        if (value == null) return null;
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private static String toDownloadUrl(String fileUrl) {
        return TenderIntegrationMapper.toDownloadUrl(fileUrl);
    }
}
