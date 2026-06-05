package com.xiyu.bid.performance.application.command;

import com.xiyu.bid.performance.domain.valueobject.CustomerLevel;
import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import com.xiyu.bid.performance.domain.valueobject.DockingMethod;
import com.xiyu.bid.performance.domain.valueobject.ProjectType;

import java.time.LocalDate;
import java.util.List;

/**
 * 业绩新增/更新命令（蓝图 4.5）
 * 注意：status 不在命令中，由系统自动计算
 */
public record PerformanceUpsertCommand(
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
        List<AttachmentEntry> attachments
) {
    public record AttachmentEntry(String fileName, String fileUrl, String fileType) {}
}
