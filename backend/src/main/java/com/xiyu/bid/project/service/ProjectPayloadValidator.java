// Input: ProjectDTO 载荷 + creating 标志
// Output: 归一化后的 ProjectDTO（trim/枚举/团队成员规范化）
// Pos: project/service
// 维护声明: 维护项目创建/更新载荷的统一校验与归一化；新增透传字段（如 stage）须在此显式保留。
package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.dto.ProjectDTO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 项目 DTO 校验与规范化纯核心.
 * 无 Spring 依赖，无副作用.
 */
final class ProjectPayloadValidator {

    private ProjectPayloadValidator() {
    }

    static ProjectDTO validateAndNormalize(ProjectDTO dto, boolean creating) {
        if (dto == null) throw new IllegalArgumentException("Project payload is required");
        if (dto.getName() == null || dto.getName().trim().isEmpty()) throw new IllegalArgumentException("Project name is required");
        if (dto.getTenderId() == null) throw new IllegalArgumentException("Tender ID is required");
        if (dto.getManagerId() == null) throw new IllegalArgumentException("Manager ID is required");
        if (creating && dto.getStatus() != null && dto.getStatus().isTerminal())
            throw new IllegalArgumentException("New projects cannot be created directly in a terminal status");

        Project.Status normStatus = creating && dto.getStatus() == null ? Project.Status.PENDING_INITIATION : dto.getStatus();
        List<Long> normTeam = (creating || dto.getTeamMembers() != null) ? normalizeTeamMembers(dto.getTeamMembers()) : null;

        return ProjectDTO.builder()
                .id(dto.getId()).name(dto.getName().trim()).tenderId(dto.getTenderId()).status(normStatus)
                // PRD §5.4 stage 透传（创建时可指定初始阶段，缺省由实体默认 INITIATED）
                .stage(dto.getStage())
                .managerId(dto.getManagerId()).teamMembers(normTeam).startDate(dto.getStartDate()).endDate(dto.getEndDate())
                .sourceModule(trimToNull(dto.getSourceModule())).sourceCustomerId(trimToNull(dto.getSourceCustomerId()))
                .sourceCustomer(trimToNull(dto.getSourceCustomer())).sourceOpportunityId(trimToNull(dto.getSourceOpportunityId()))
                .sourceReasoningSummary(trimToNull(dto.getSourceReasoningSummary()))
                .competitorAnalysisJson(dto.getCompetitorAnalysisJson()).tasksJson(dto.getTasksJson())
                .aiAnalysisJson(dto.getAiAnalysisJson())
                .customer(trimToNull(dto.getCustomer())).budget(dto.getBudget())
                .industry(trimToNull(dto.getIndustry())).customerType(trimToNull(dto.getCustomerType()))
                .region(trimToNull(dto.getRegion())).platform(trimToNull(dto.getPlatform()))
                .deadline(dto.getDeadline()).description(trimToNull(dto.getDescription()))
                .remark(trimToNull(dto.getRemark())).tagsJson(trimToNull(dto.getTagsJson()))
                .customerManager(trimToNull(dto.getCustomerManager()))
                .customerManagerId(trimToNull(dto.getCustomerManagerId()))
                // CO-601: Map 结构透传（无需 trim），scope 过滤在 Service 层 CustomFieldsCodec
                .customFields(dto.getCustomFields())
                .build();
    }

    static List<Long> normalizeTeamMembers(List<Long> teamMembers) {
        if (teamMembers == null) return List.of();
        return teamMembers.stream().filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
    }

    static String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
