package com.xiyu.bid.performance.domain.model;

import com.xiyu.bid.performance.domain.valueobject.CustomerLevel;
import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import com.xiyu.bid.performance.domain.valueobject.DockingMethod;
import com.xiyu.bid.performance.domain.valueobject.ProjectType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 业绩记录领域模型（蓝图 4.5）
 * 以合同为核心的不可变值对象
 */
public record PerformanceRecord(
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
        List<AttachmentEntry> attachments,
        // 系统字段
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public record AttachmentEntry(
            Long id,
            String fileName,
            String fileUrl,
            String fileType
    ) {}

    public PerformanceRecord {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
