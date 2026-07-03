package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.warehouse.domain.WarehouseActionType;
import com.xiyu.bid.warehouse.domain.WarehouseImportPolicy;
import com.xiyu.bid.warehouse.domain.WarehouseImportRow;
import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import com.xiyu.bid.warehouse.service.WarehouseLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 仓库导入行持久化器：实现"存在更新 / 不存在新增"策略。
 */
@Component
@RequiredArgsConstructor
public class WarehouseImportRowPersister {

    private final WarehouseRepository warehouseRepo;
    private final WarehouseLogService warehouseLogService;

    @Transactional
    public WarehouseEntity persist(WarehouseImportRow row, User operator) {
        WarehouseEntity entity = warehouseRepo.findByName(row.sanitizedName).orElse(null);
        boolean isCreate = (entity == null);
        if (isCreate) {
            entity = WarehouseEntity.builder()
                    .name(row.sanitizedName)
                    .type(row.type)
                    .region(row.region)
                    .province(row.province)
                    .address(row.address)
                    .area(row.area)
                    .contactPerson(row.contactPerson)
                    .remarks(row.remarks)
                    .startDate(row.startDate)
                    .endDate(row.endDate)
                    .lessor(row.lessor)
                    .lessee(row.lessee)
                    .invoicePeriod(null)
                    .invoicePeriodStart(row.invoicePeriodStart)
                    .invoicePeriodEnd(row.invoicePeriodEnd)
                    .closePlan(row.closePlan)
                    .hasPropertyCert(row.hasPropertyCert)
                    .hasInvoice(row.hasInvoice)
                    .hasPhotos(row.hasPhotos)
                    .hasLeaseContract(row.hasLeaseContract)
                    .certRemarks(row.certRemarks)
                    .status(WarehouseStatus.IN_USE)
                    .createdBy(operator.getId())
                    .build();
        } else {
            entity.setType(row.type);
            entity.setRegion(row.region);
            entity.setProvince(row.province);
            entity.setAddress(row.address);
            entity.setArea(row.area);
            entity.setContactPerson(row.contactPerson);
            entity.setRemarks(row.remarks);
            entity.setStartDate(row.startDate);
            entity.setEndDate(row.endDate);
            entity.setLessor(row.lessor);
            entity.setLessee(row.lessee);
            entity.setInvoicePeriod(null);
            entity.setInvoicePeriodStart(row.invoicePeriodStart);
            entity.setInvoicePeriodEnd(row.invoicePeriodEnd);
            entity.setClosePlan(row.closePlan);
            entity.setHasPropertyCert(row.hasPropertyCert);
            entity.setHasInvoice(row.hasInvoice);
            entity.setHasPhotos(row.hasPhotos);
            entity.setHasLeaseContract(row.hasLeaseContract);
            entity.setCertRemarks(row.certRemarks);
            entity.setUpdatedBy(operator.getId());
        }
        WarehouseEntity saved = warehouseRepo.save(entity);
        WarehouseActionType action = isCreate ? WarehouseActionType.CREATE : WarehouseActionType.EDIT;
        String desc = isCreate
                ? "批量导入新增 - " + saved.getName()
                : "批量导入更新 - " + saved.getName();
        warehouseLogService.saveLog(saved, action, null, null, null, desc,
                operator.getFullName() + "(" + operator.getUsername() + ")",
                operator.getId());
        return saved;
    }
}
