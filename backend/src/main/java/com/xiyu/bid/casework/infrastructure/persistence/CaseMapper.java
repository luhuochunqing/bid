package com.xiyu.bid.casework.infrastructure.persistence;

import com.xiyu.bid.casework.dto.CaseDTO;
import com.xiyu.bid.entity.Case;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CaseMapper {

    public Case toEntity(CaseDTO dto) {
        if (dto == null) {
            return null;
        }
        return Case.builder()
                .id(dto.getId())
                .title(dto.getTitle())
                .industry(dto.getIndustry() == null ? null : Case.Industry.valueOf(dto.getIndustry().name()))
                .outcome(dto.getOutcome() == null ? null : Case.Outcome.valueOf(dto.getOutcome().name()))
                .amount(dto.getAmount())
                .projectDate(dto.getProjectDate())
                .description(dto.getDescription())
                .customerName(dto.getCustomerName())
                .locationName(dto.getLocationName())
                .projectPeriod(dto.getProjectPeriod())
                .productLine(dto.getProductLine())
                .sourceProjectId(dto.getSourceProjectId())
                .archiveSummary(dto.getArchiveSummary())
                .priceStrategy(dto.getPriceStrategy())
                .successFactors(copyList(dto.getSuccessFactors()))
                .lessonsLearned(copyList(dto.getLessonsLearned()))
                .documentSnapshotText(dto.getDocumentSnapshotText())
                .attachmentNames(copyList(dto.getAttachmentNames()))
                .status(dto.getStatus())
                .publishedAt(dto.getPublishedAt())
                .visibility(dto.getVisibility())
                .searchDocument(dto.getSearchDocument())
                .tags(copyList(dto.getTags()))
                .highlights(copyList(dto.getHighlights()))
                .technologies(copyList(dto.getTechnologies()))
                .viewCount(dto.getViewCount())
                .useCount(dto.getUseCount())
                .build();
    }

    public CaseDTO toDTO(Case entity) {
        if (entity == null) {
            return null;
        }
        return CaseDTO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .industry(entity.getIndustry() == null ? null : CaseDTO.Industry.valueOf(entity.getIndustry().name()))
                .outcome(entity.getOutcome() == null ? null : CaseDTO.Outcome.valueOf(entity.getOutcome().name()))
                .amount(entity.getAmount())
                .projectDate(entity.getProjectDate())
                .description(entity.getDescription())
                .customerName(entity.getCustomerName())
                .locationName(entity.getLocationName())
                .projectPeriod(entity.getProjectPeriod())
                .productLine(entity.getProductLine())
                .sourceProjectId(entity.getSourceProjectId())
                .archiveSummary(entity.getArchiveSummary())
                .priceStrategy(entity.getPriceStrategy())
                .successFactors(copyList(entity.getSuccessFactors()))
                .lessonsLearned(copyList(entity.getLessonsLearned()))
                .documentSnapshotText(entity.getDocumentSnapshotText())
                .attachmentNames(copyList(entity.getAttachmentNames()))
                .status(entity.getStatus())
                .publishedAt(entity.getPublishedAt())
                .visibility(entity.getVisibility())
                .searchDocument(entity.getSearchDocument())
                .tags(copyList(entity.getTags()))
                .highlights(copyList(entity.getHighlights()))
                .technologies(copyList(entity.getTechnologies()))
                .viewCount(entity.getViewCount())
                .useCount(entity.getUseCount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public void applyUpdates(Case target, CaseDTO updates) {
        if (target == null || updates == null) {
            return;
        }
        if (updates.getTitle() != null) target.setTitle(updates.getTitle());
        if (updates.getIndustry() != null) target.setIndustry(Case.Industry.valueOf(updates.getIndustry().name()));
        if (updates.getOutcome() != null) target.setOutcome(Case.Outcome.valueOf(updates.getOutcome().name()));
        if (updates.getAmount() != null) target.setAmount(updates.getAmount());
        if (updates.getProjectDate() != null) target.setProjectDate(updates.getProjectDate());
        if (updates.getDescription() != null) target.setDescription(updates.getDescription());
        if (updates.getCustomerName() != null) target.setCustomerName(updates.getCustomerName());
        if (updates.getLocationName() != null) target.setLocationName(updates.getLocationName());
        if (updates.getProjectPeriod() != null) target.setProjectPeriod(updates.getProjectPeriod());
        if (updates.getProductLine() != null) target.setProductLine(updates.getProductLine());
        if (updates.getSourceProjectId() != null) target.setSourceProjectId(updates.getSourceProjectId());
        if (updates.getArchiveSummary() != null) target.setArchiveSummary(updates.getArchiveSummary());
        if (updates.getPriceStrategy() != null) target.setPriceStrategy(updates.getPriceStrategy());
        if (updates.getSuccessFactors() != null) target.setSuccessFactors(copyList(updates.getSuccessFactors()));
        if (updates.getLessonsLearned() != null) target.setLessonsLearned(copyList(updates.getLessonsLearned()));
        if (updates.getDocumentSnapshotText() != null) target.setDocumentSnapshotText(updates.getDocumentSnapshotText());
        if (updates.getAttachmentNames() != null) target.setAttachmentNames(copyList(updates.getAttachmentNames()));
        if (updates.getStatus() != null) target.setStatus(updates.getStatus());
        if (updates.getPublishedAt() != null) target.setPublishedAt(updates.getPublishedAt());
        if (updates.getVisibility() != null) target.setVisibility(updates.getVisibility());
        if (updates.getSearchDocument() != null) target.setSearchDocument(updates.getSearchDocument());
        if (updates.getTags() != null) target.setTags(copyList(updates.getTags()));
        if (updates.getHighlights() != null) target.setHighlights(copyList(updates.getHighlights()));
        if (updates.getTechnologies() != null) target.setTechnologies(copyList(updates.getTechnologies()));
        if (updates.getViewCount() != null) target.setViewCount(updates.getViewCount());
        if (updates.getUseCount() != null) target.setUseCount(updates.getUseCount());
    }

    private List<String> copyList(List<String> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }
}
