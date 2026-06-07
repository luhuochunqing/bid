package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.dto.ProjectDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Bidirectional mapper between Project entity and ProjectDTO.
 * Replaces the duplicated convertToDTO / convertToEntity methods in ProjectService.
 */
public final class ProjectMapper {

    private ProjectMapper() {}

    public static ProjectDTO toDTO(Project p) {
        return ProjectDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .tenderId(p.getTenderId())
                .status(p.getStatus())
                .managerId(p.getManagerId())
                .teamMembers(p.getTeamMembers() == null ? List.of() : new ArrayList<>(p.getTeamMembers()))
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .sourceModule(p.getSourceModule())
                .sourceCustomerId(p.getSourceCustomerId())
                .sourceCustomer(p.getSourceCustomer())
                .sourceOpportunityId(p.getSourceOpportunityId())
                .sourceReasoningSummary(p.getSourceReasoningSummary())
                .competitorAnalysisJson(p.getCompetitorAnalysisJson())
                .tasksJson(p.getTasksJson())
                .aiAnalysisJson(p.getAiAnalysisJson())
                .customer(p.getCustomer())
                .budget(p.getBudget())
                .industry(p.getIndustry())
                .customerType(p.getCustomerType())
                .region(p.getRegion())
                .platform(p.getPlatform())
                .deadline(p.getDeadline())
                .description(p.getDescription())
                .remark(p.getRemark())
                .tagsJson(p.getTagsJson())
                .customerManager(p.getCustomerManager())
                .customerManagerId(p.getCustomerManagerId())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .stage(p.getStage())
                // priority mapped from customerGrade via service layer (list projection query)
                .build();
    }

    public static Project toEntity(ProjectDTO dto) {
        return Project.builder()
                .id(dto.getId())
                .name(dto.getName())
                .tenderId(dto.getTenderId())
                .status(dto.getStatus() != null ? dto.getStatus() : Project.Status.PENDING_INITIATION)
                .managerId(dto.getManagerId())
                .teamMembers(dto.getTeamMembers() != null ? dto.getTeamMembers() : List.of())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .sourceModule(dto.getSourceModule())
                .sourceCustomerId(dto.getSourceCustomerId())
                .sourceCustomer(dto.getSourceCustomer())
                .sourceOpportunityId(dto.getSourceOpportunityId())
                .sourceReasoningSummary(dto.getSourceReasoningSummary())
                .competitorAnalysisJson(dto.getCompetitorAnalysisJson())
                .tasksJson(dto.getTasksJson())
                .aiAnalysisJson(dto.getAiAnalysisJson())
                .customer(dto.getCustomer())
                .budget(dto.getBudget())
                .industry(dto.getIndustry())
                .customerType(dto.getCustomerType())
                .region(dto.getRegion())
                .platform(dto.getPlatform())
                .deadline(dto.getDeadline())
                .description(dto.getDescription())
                .remark(dto.getRemark())
                .tagsJson(dto.getTagsJson())
                .customerManager(dto.getCustomerManager())
                .customerManagerId(dto.getCustomerManagerId())
                .build();
    }
}
