package com.xiyu.bid.performance.application.mapper;

import com.xiyu.bid.performance.application.command.PerformanceUpsertCommand;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.domain.model.PerformanceRecord;
import com.xiyu.bid.performance.domain.service.ContractStatusPolicy;
import com.xiyu.bid.performance.domain.valueobject.ContractStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 业绩 Mapper（蓝图 4.5）
 * 负责 Domain ↔ DTO 转换，包含状态计算
 */
@Component
public class PerformanceMapper {

    public PerformanceDTO toDTO(PerformanceRecord r) {
        if (r == null) return null;
        return toDTO(r, (LocalDate) null);
    }

    /**
     * CO-583: 重载方法，注入集团聚合总截止日期。
     *
     * @param groupTotalExpiryMap 集团 → MAX(expiryDate) 映射（基于全量数据，不受筛选影响）；
     *                            为 null 或不含当前集团时，groupTotalExpiryDate 返回 null
     */
    public PerformanceDTO toDTO(PerformanceRecord r, Map<String, LocalDate> groupTotalExpiryMap) {
        if (r == null) return null;
        LocalDate groupTotal = groupTotalExpiryMap != null && r.groupCompany() != null
                ? groupTotalExpiryMap.get(r.groupCompany())
                : null;
        return toDTO(r, groupTotal);
    }

    /**
     * CO-583: 单值重载，详情页等单记录场景直接传入聚合值，避免构造 Map。
     *
     * @param groupTotalExpiryDate 集团聚合总截止日期；null 表示无聚合值
     */
    public PerformanceDTO toDTO(PerformanceRecord r, LocalDate groupTotalExpiryDate) {
        if (r == null) return null;
        LocalDate today = LocalDate.now();

        Long daysRemaining = ContractStatusPolicy.calculateDaysRemaining(r.expiryDate(), today);
        String expiryReminder = ContractStatusPolicy.calculateExpiryReminder(
                r.customerType(), r.expiryDate(), today);
        ContractStatus status = ContractStatusPolicy.calculateStatus(
                r.customerType(), r.expiryDate(), today);

        var atts = r.attachments().stream()
                .map(this::toAttachmentDTO)
                .toList();

        return new PerformanceDTO(
                r.id(),
                r.contractName(), r.signingEntity(), r.groupCompany(),
                r.customerType(), r.industry(),
                r.projectType(), r.dockingMethod(), r.customerLevel(),
                r.signingDate(), r.expiryDate(), r.totalExpiryDate(),
                groupTotalExpiryDate,
                daysRemaining, expiryReminder, status,
                r.contactPerson(), r.contactInfo(), r.territory(),
                r.customerAddress(), r.xiyuProjectManager(),
                r.mallWebsiteUrl(), r.hasBidNotice(), r.remarks(),
                atts, r.createdAt(), r.updatedAt()
        );
    }

    public PerformanceDTO.AttachmentDTO toAttachmentDTO(PerformanceRecord.AttachmentEntry a) {
        if (a == null) return null;
        return new PerformanceDTO.AttachmentDTO(a.id(), a.fileName(), a.fileUrl(), a.fileType());
    }

    public PerformanceRecord toRecord(PerformanceUpsertCommand cmd) {
        var atts = toAttachmentEntries(cmd.attachments());
        return new PerformanceRecord(
                null,
                cmd.contractName(), cmd.signingEntity(), cmd.groupCompany(),
                cmd.customerType(), cmd.industry(),
                cmd.projectType(), cmd.dockingMethod(), cmd.customerLevel(),
                cmd.signingDate(), cmd.expiryDate(), cmd.totalExpiryDate(),
                cmd.contactPerson(), cmd.contactInfo(), cmd.territory(),
                cmd.customerAddress(), cmd.xiyuProjectManager(),
                cmd.mallWebsiteUrl(), cmd.hasBidNotice(), cmd.remarks(),
                atts, null, null
        );
    }

    public List<PerformanceRecord.AttachmentEntry> toAttachmentEntries(
            List<PerformanceUpsertCommand.AttachmentEntry> attachments) {
        if (attachments == null) return List.of();
        return attachments.stream()
                .filter(a -> a != null)
                .map(this::toEntry)
                .toList();
    }

    private PerformanceRecord.AttachmentEntry toEntry(PerformanceUpsertCommand.AttachmentEntry a) {
        return new PerformanceRecord.AttachmentEntry(null, a.fileName(), a.fileUrl(), a.fileType());
    }
}
