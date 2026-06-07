package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.dto.ProjectDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Utility for applying DTO field updates to an existing Project entity.
 * Replaces the repetitive applyProjectUpdates method in ProjectService.
 */
public final class ProjectUpdateApplier {

    private ProjectUpdateApplier() {}

    public static void apply(Project target, ProjectDTO updates, Consumer<Project.Status> statusChanger) {
        if (updates.getName() != null) target.setName(updates.getName());
        if (updates.getTenderId() != null) target.setTenderId(updates.getTenderId());
        if (updates.getStatus() != null) statusChanger.accept(updates.getStatus());
        if (updates.getManagerId() != null) target.setManagerId(updates.getManagerId());
        if (updates.getStartDate() != null) target.setStartDate(updates.getStartDate());
        if (updates.getEndDate() != null) target.setEndDate(updates.getEndDate());
        if (updates.getTeamMembers() != null) target.setTeamMembers(normalizeTeamMembers(updates.getTeamMembers()));
        if (updates.getSourceModule() != null) target.setSourceModule(updates.getSourceModule());
        if (updates.getSourceCustomerId() != null) target.setSourceCustomerId(updates.getSourceCustomerId());
        if (updates.getSourceCustomer() != null) target.setSourceCustomer(updates.getSourceCustomer());
        if (updates.getSourceOpportunityId() != null) target.setSourceOpportunityId(updates.getSourceOpportunityId());
        if (updates.getSourceReasoningSummary() != null) target.setSourceReasoningSummary(updates.getSourceReasoningSummary());
        if (updates.getCompetitorAnalysisJson() != null) target.setCompetitorAnalysisJson(updates.getCompetitorAnalysisJson());
        if (updates.getTasksJson() != null) target.setTasksJson(updates.getTasksJson());
        if (updates.getAiAnalysisJson() != null) target.setAiAnalysisJson(updates.getAiAnalysisJson());
        target.setCustomer(updates.getCustomer());
        target.setBudget(updates.getBudget());
        target.setIndustry(updates.getIndustry());
        target.setCustomerType(updates.getCustomerType());
        target.setRegion(updates.getRegion());
        target.setPlatform(updates.getPlatform());
        target.setDeadline(updates.getDeadline());
        target.setDescription(updates.getDescription());
        target.setRemark(updates.getRemark());
        target.setTagsJson(updates.getTagsJson());
        if (updates.getCustomerManager() != null) target.setCustomerManager(updates.getCustomerManager());
        if (updates.getCustomerManagerId() != null) target.setCustomerManagerId(updates.getCustomerManagerId());
    }

    private static List<Long> normalizeTeamMembers(List<Long> teamMembers) {
        if (teamMembers == null) return List.of();
        return teamMembers.stream().filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new),
                        ArrayList::new));
    }
}
