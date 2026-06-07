// checkstyle:off
package com.xiyu.bid.warehouse.service;

import com.xiyu.bid.warehouse.dto.WarehouseAttachmentDTO;
import com.xiyu.bid.warehouse.dto.WarehouseDTO;
import com.xiyu.bid.warehouse.dto.WarehouseDetailDTO;
import com.xiyu.bid.warehouse.dto.WarehouseOperationLogDTO;
import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseOperationLogEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WarehouseMapper {

    public WarehouseEntity toEntity(WarehouseDTO d) {
        return WarehouseEntity.builder()
                .name(d.getName()).type(d.getType()).region(d.getRegion()).province(d.getProvince())
                .address(d.getAddress()).area(d.getArea()).contactPerson(d.getContactPerson()).remarks(d.getRemarks())
                .startDate(d.getStartDate()).endDate(d.getEndDate()).lessor(d.getLessor()).lessee(d.getLessee())
                .invoicePeriod(d.getInvoicePeriod()).closePlan(d.getClosePlan())
                .hasPropertyCert(d.getHasPropertyCert()).hasInvoice(d.getHasInvoice()).hasPhotos(d.getHasPhotos())
                .certRemarks(d.getCertRemarks()).build();
    }

    public void mergeEntity(WarehouseEntity e, WarehouseDTO d) {
        if (d.getName() != null) e.setName(d.getName());
        if (d.getType() != null) e.setType(d.getType());
        if (d.getRegion() != null) e.setRegion(d.getRegion());
        if (d.getProvince() != null) e.setProvince(d.getProvince());
        if (d.getAddress() != null) e.setAddress(d.getAddress());
        if (d.getArea() != null) e.setArea(d.getArea());
        if (d.getContactPerson() != null) e.setContactPerson(d.getContactPerson());
        if (d.getRemarks() != null) e.setRemarks(d.getRemarks());
        if (d.getStartDate() != null) e.setStartDate(d.getStartDate());
        if (d.getEndDate() != null) e.setEndDate(d.getEndDate());
        if (d.getLessor() != null) e.setLessor(d.getLessor());
        if (d.getLessee() != null) e.setLessee(d.getLessee());
        if (d.getInvoicePeriod() != null) e.setInvoicePeriod(d.getInvoicePeriod());
        if (d.getClosePlan() != null) e.setClosePlan(d.getClosePlan());
        if (d.getHasPropertyCert() != null) e.setHasPropertyCert(d.getHasPropertyCert());
        if (d.getHasInvoice() != null) e.setHasInvoice(d.getHasInvoice());
        if (d.getHasPhotos() != null) e.setHasPhotos(d.getHasPhotos());
        if (d.getCertRemarks() != null) e.setCertRemarks(d.getCertRemarks());
    }

    public WarehouseDetailDTO toDetailDTO(WarehouseEntity e,
                                          List<WarehouseAttachmentEntity> attachments,
                                          List<WarehouseOperationLogEntity> logs) {
        return new WarehouseDetailDTO(
                e.getId(), e.getName(),
                WarehouseDetailDTO.WarehouseTypeEnum.valueOf(e.getType().name()),
                e.getRegion(), e.getProvince(), e.getAddress(), e.getArea(),
                e.getContactPerson(), e.getRemarks(),
                e.getStartDate(), e.getEndDate(),
                e.getLessor(), e.getLessee(),
                e.getInvoicePeriod(), e.getClosePlan(), e.getCloseReason(),
                e.getHasPropertyCert(), e.getHasInvoice(), e.getHasPhotos(),
                e.getCertRemarks(),
                WarehouseDetailDTO.WarehouseStatusEnum.valueOf(e.getStatus().name()),
                e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt(), e.getVersion(),
                attachments.stream().map(this::toAttachmentDTO).toList(),
                logs.stream().map(this::toLogDTO).toList()
        );
    }

    public WarehouseAttachmentDTO toAttachmentDTO(WarehouseAttachmentEntity a) {
        return new WarehouseAttachmentDTO(
                a.getId(), a.getType(), a.getOriginalFilename(),
                a.getStoredFilename(), a.getFileSize(), a.getContentType(), a.getUploadedAt()
        );
    }

    public WarehouseOperationLogDTO toLogDTO(WarehouseOperationLogEntity l) {
        return new WarehouseOperationLogDTO(
                l.getId(), l.getCreatedAt(), l.getOperatorUsername(),
                l.getActionType(), l.getFieldName(), l.getOldValue(), l.getNewValue(),
                l.getDescription()
        );
    }
}
