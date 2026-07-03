package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.core.InitiationFieldPolicy;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.core.ProjectStatusPolicy;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;

import java.util.Map;

final class ProjectListEnrichmentSupport {

    private ProjectListEnrichmentSupport() {
    }

    static void populateFromTender(
            final ProjectDTO dto,
            final Map<Long, Tender> tenderMap) {
        Long tenderId = dto.getTenderId();
        if (tenderId == null) {
            return;
        }
        Tender t = tenderMap.get(tenderId);
        if (t == null) {
            if (dto.getProjectLeaderId() == null
                    && dto.getManagerId() != null) {
                dto.setProjectLeaderId(dto.getManagerId());
            }
            return;
        }
        if (dto.getOwnerUnit() == null && t.getPurchaserName() != null) {
            dto.setOwnerUnit(t.getPurchaserName());
        }
        if (dto.getBidOpenTime() == null && t.getBidOpeningTime() != null) {
            dto.setBidOpenTime(t.getBidOpeningTime());
        }
        if (dto.getProjectType() == null && t.getProjectType() != null) {
            String normalized = InitiationFieldPolicy.normalizeProjectType(t.getProjectType());
            dto.setProjectType(normalized != null ? normalized : t.getProjectType());
        }
        if (dto.getCustomerType() == null && t.getCustomerType() != null) {
            String normalized = InitiationFieldPolicy.normalizeCustomerType(t.getCustomerType());
            dto.setCustomerType(normalized != null ? normalized : t.getCustomerType());
        }
        if (dto.getRegion() == null && t.getRegion() != null) {
            dto.setRegion(t.getRegion());
        }
        if (dto.getPriority() == null && t.getPriority() != null) {
            String normalized = InitiationFieldPolicy.normalizePriority(t.getPriority());
            dto.setPriority(normalized != null ? normalized : t.getPriority());
        }
        if (dto.getBiddingPlatform() == null && t.getSourcePlatform() != null) {
            dto.setBiddingPlatform(t.getSourcePlatform());
        }
        if (dto.getBidMonth() == null && t.getBidOpeningTime() != null) {
            dto.setBidMonth(t.getBidOpeningTime()
                    .toLocalDate()
                    .toString()
                    .substring(0, 7));
        }
        if (dto.getSourceModule() == null && t.getSourceType() != null) {
            dto.setSourceModule(t.getSourceType().getLabel());
        }
        if (dto.getBudget() == null && t.getBudget() != null) {
            dto.setBudget(t.getBudget());
        }
        if (dto.getProjectLeaderId() == null && t.getProjectManagerId() != null) {
            dto.setProjectLeaderId(t.getProjectManagerId());
        }
        if (dto.getProjectLeaderId() == null && dto.getManagerId() != null) {
            dto.setProjectLeaderId(dto.getManagerId());
        }
        if (dto.getBiddingLeaderId() == null && t.getBiddingPersonId() != null) {
            dto.setBiddingLeaderId(t.getBiddingPersonId());
        }
        if (isBlank(dto.getProjectLeaderName())
                && !isBlank(t.getProjectManagerName())) {
            dto.setProjectLeaderName(t.getProjectManagerName());
        }
        if (isBlank(dto.getLeaderDepartment()) && !isBlank(t.getDepartment())) {
            dto.setLeaderDepartment(t.getDepartment());
        }
        if (isBlank(dto.getBiddingLeaderName())
                && !isBlank(t.getBiddingPersonName())) {
            dto.setBiddingLeaderName(t.getBiddingPersonName());
        }
    }

    static ProjectStage resolveStage(final String stageValue) {
        if (stageValue == null || stageValue.isBlank()) {
            return ProjectStage.INITIATED;
        }
        try {
            return ProjectStage.valueOf(stageValue.trim());
        } catch (IllegalArgumentException ex) {
            return ProjectStage.INITIATED;
        }
    }

    static boolean isInitiationSubmitted(
            final ProjectInitiationDetails details) {
        if (details == null || details.getReviewStatus() == null) {
            return false;
        }
        return switch (details.getReviewStatus()) {
            case "PENDING_REVIEW", "APPROVED" -> true;
            case "DRAFT", "REJECTED" -> false;
            default -> false;
        };
    }

    static String computeBidStatus(
            final ProjectStage stage,
            final String bidResult,
            final boolean submitted) {
        return ProjectStatusPolicy.compute(stage, bidResult, submitted).name();
    }

    private static boolean isBlank(final String s) {
        return s == null || s.isBlank();
    }
}
