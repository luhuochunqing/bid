package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.dto.ProjectDTO;

import java.util.Map;

/**
 * 从 Tender 填充 ProjectDTO 列表投影字段（纯核心，无副作用）。
 * Tender 值仅在 DTO 字段为 null 时回填，不覆盖项目立项详情已有值。
 */
final class ProjectTenderPopulator {

    private ProjectTenderPopulator() {
    }

    static void populate(ProjectDTO dto, Map<Long, Tender> tenderMap) {
        Long tenderId = dto.getTenderId();
        if (tenderId == null) return;
        Tender t = tenderMap.get(tenderId);
        if (t == null) return;
        if (dto.getOwnerUnit() == null && t.getPurchaserName() != null) dto.setOwnerUnit(t.getPurchaserName());
        if (dto.getBidOpenTime() == null && t.getBidOpeningTime() != null) dto.setBidOpenTime(t.getBidOpeningTime());
        if (dto.getProjectType() == null && t.getProjectType() != null) dto.setProjectType(t.getProjectType());
        if (dto.getCustomerType() == null && t.getCustomerType() != null) dto.setCustomerType(t.getCustomerType());
        if (dto.getRegion() == null && t.getRegion() != null) dto.setRegion(t.getRegion());
        if (dto.getPriority() == null && t.getPriority() != null) dto.setPriority(t.getPriority());
        if (dto.getBiddingPlatform() == null && t.getSourcePlatform() != null) dto.setBiddingPlatform(t.getSourcePlatform());
        if (dto.getBidMonth() == null && t.getBidOpeningTime() != null) {
            dto.setBidMonth(t.getBidOpeningTime().toLocalDate().toString().substring(0, 7));
        }
        // sourceModule 收敛到 Tender.SourceType 分类（中文 label），与项目列表 UI 筛选项对齐：
        // 具体招标平台名（如"建工招采"）由 biddingPlatform 字段承载。
        if (dto.getSourceModule() == null && t.getSourceType() != null) dto.setSourceModule(t.getSourceType().getLabel());
        if (dto.getBudget() == null && t.getBudget() != null) dto.setBudget(t.getBudget());
        // Leader fields: fallback to tender when not populated by initiation details
        if (isBlank(dto.getProjectLeaderName()) && !isBlank(t.getProjectManagerName()))
            dto.setProjectLeaderName(t.getProjectManagerName());
        if (isBlank(dto.getLeaderDepartment()) && !isBlank(t.getDepartment()))
            dto.setLeaderDepartment(t.getDepartment());
        if (isBlank(dto.getBiddingLeaderName()) && !isBlank(t.getBiddingPersonName()))
            dto.setBiddingLeaderName(t.getBiddingPersonName());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
