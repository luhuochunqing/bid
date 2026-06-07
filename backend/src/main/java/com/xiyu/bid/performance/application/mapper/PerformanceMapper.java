package com.xiyu.bid.performance.application.mapper;

import com.xiyu.bid.performance.application.command.PerformanceUpsertCommand;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.domain.model.PerformanceRecord;
import com.xiyu.bid.performance.domain.service.ContractStatusPolicy;
import com.xiyu.bid.performance.domain.valueobject.ContractStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 业绩 Mapper（蓝图 4.5）
 * 负责 Domain ↔ DTO 转换，包含状态计算
 */
@Component
public class PerformanceMapper {

    public PerformanceDTO toDTO(PerformanceRecord r) {
        if (r == null) return null;
        LocalDate today = LocalDate.now();

        long daysRemaining = ContractStatusPolicy.calculateDaysRemaining(r.expiryDate(), today);
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
