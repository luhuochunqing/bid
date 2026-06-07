package com.xiyu.bid.performance.application.dto;

import com.xiyu.bid.performance.domain.valueobject.ContractStatus;
import com.xiyu.bid.performance.domain.valueobject.CustomerLevel;
import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import com.xiyu.bid.performance.domain.valueobject.DockingMethod;
import com.xiyu.bid.performance.domain.valueobject.ProjectType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 业绩管理 DTO（蓝图 4.5）
 * 包含系统自动计算的字段：status, daysRemaining, expiryReminder
 */
public record PerformanceDTO(
        Long id,
        // 合同基础信息
        String contractName,
        String signingEntity,
        String groupCompany,
        CustomerType customerType,
        String industry,
        ProjectType projectType,
        DockingMethod dockingMethod,
        CustomerLevel customerLevel,
        // 合同关键日期
        LocalDate signingDate,
        LocalDate expiryDate,
        LocalDate totalExpiryDate,
        // 系统计算字段
        long daysRemaining,
        String expiryReminder,
        ContractStatus status,
        // 客户与联系人
        String contactPerson,
        String contactInfo,
        String territory,
        String customerAddress,
        String xiyuProjectManager,
        // 附件资料
        String mallWebsiteUrl,
        boolean hasBidNotice,
        String remarks,
        List<AttachmentDTO> attachments,
        // 系统字段
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record AttachmentDTO(Long id, String fileName, String fileUrl, String fileType) {}
}
