// checkstyle:off
package com.xiyu.bid.warehouse.service;

import com.xiyu.bid.warehouse.domain.WarehouseActionType;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseOperationLogEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseOperationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WarehouseLogService {

    private final WarehouseOperationLogRepository oplogRepo;

    public void saveLog(WarehouseEntity warehouse, WarehouseActionType actionType, String fieldName, String oldValue, String newValue, String description, String operatorUsername, Long operatorId) {
        WarehouseOperationLogEntity logEntity = WarehouseOperationLogEntity.builder()
                .warehouse(warehouse)
                .actionType(actionType)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .description(description)
                .operatorId(operatorId)
                .operatorUsername(operatorUsername)
                .createdAt(LocalDateTime.now())
                .build();
        oplogRepo.save(logEntity);
    }

    @Transactional
    public void logEntityChanges(WarehouseEntity oldVal, WarehouseEntity newVal, String operatorUsername, Long operatorId) {
        if (!Objects.equals(oldVal.getName(), newVal.getName())) {
            saveLog(newVal, WarehouseActionType.EDIT, "仓库名称", oldVal.getName(), newVal.getName(), "修改仓库名称：" + oldVal.getName() + " -> " + newVal.getName(), operatorUsername, operatorId);
        }
        if (!Objects.equals(oldVal.getType(), newVal.getType())) {
            saveLog(newVal, WarehouseActionType.EDIT, "仓库类型", String.valueOf(oldVal.getType()), String.valueOf(newVal.getType()), "修改仓库类型：" + oldVal.getType() + " -> " + newVal.getType(), operatorUsername, operatorId);
        }
        if (!Objects.equals(oldVal.getRegion(), newVal.getRegion())) {
            saveLog(newVal, WarehouseActionType.EDIT, "所属区域", oldVal.getRegion(), newVal.getRegion(), "修改所属区域：" + oldVal.getRegion() + " -> " + newVal.getRegion(), operatorUsername, operatorId);
        }
        if (!Objects.equals(oldVal.getProvince(), newVal.getProvince())) {
            saveLog(newVal, WarehouseActionType.EDIT, "所在省份", oldVal.getProvince(), newVal.getProvince(), "修改所在省份：" + oldVal.getProvince() + " -> " + newVal.getProvince(), operatorUsername, operatorId);
        }
        if (!Objects.equals(oldVal.getAddress(), newVal.getAddress())) {
            saveLog(newVal, WarehouseActionType.EDIT, "具体地址", oldVal.getAddress(), newVal.getAddress(), "修改具体地址", operatorUsername, operatorId);
        }
        if (!Objects.equals(oldVal.getArea(), newVal.getArea())) {
            saveLog(newVal, WarehouseActionType.EDIT, "仓库面积", String.valueOf(oldVal.getArea()), String.valueOf(newVal.getArea()), "修改仓库面积：" + oldVal.getArea() + " -> " + newVal.getArea(), operatorUsername, operatorId);
        }
        if (!Objects.equals(oldVal.getContactPerson(), newVal.getContactPerson())) {
            saveLog(newVal, WarehouseActionType.EDIT, "区域联系人", oldVal.getContactPerson(), newVal.getContactPerson(), "修改区域联系人：" + oldVal.getContactPerson() + " -> " + newVal.getContactPerson(), operatorUsername, operatorId);
        }
        if (!Objects.equals(oldVal.getRemarks(), newVal.getRemarks())) {
            saveLog(newVal, WarehouseActionType.EDIT, "备注", oldVal.getRemarks(), newVal.getRemarks(), "修改备注", operatorUsername, operatorId);
        }
        if (!Objects.equals(oldVal.getStartDate(), newVal.getStartDate())) {
            saveLog(newVal, WarehouseActionType.EDIT, "开始时间", String.valueOf(oldVal.getStartDate()), String.valueOf(newVal.getStartDate()), "修改租约开始时间：" + oldVal.getStartDate() + " -> " + newVal.getStartDate(), operatorUsername, operatorId);
        }
        if (!Objects.equals(oldVal.getEndDate(), newVal.getEndDate())) {
            saveLog(newVal, WarehouseActionType.EDIT, "结束时间", String.valueOf(oldVal.getEndDate()), String.valueOf(newVal.getEndDate()), "修改租约结束时间：" + oldVal.getEndDate() + " -> " + newVal.getEndDate(), operatorUsername, operatorId);
        }
        if (!Objects.equals(oldVal.getLessor(), newVal.getLessor())) {
            saveLog(newVal, WarehouseActionType.EDIT, "出租方", oldVal.getLessor(), newVal.getLessor(), "修改出租方：" + oldVal.getLessor() + " -> " + newVal.getLessor(), operatorUsername, operatorId);
        }
        if (!Objects.equals(oldVal.getLessee(), newVal.getLessee())) {
            saveLog(newVal, WarehouseActionType.EDIT, "承租方", oldVal.getLessee(), newVal.getLessee(), "修改承租方：" + oldVal.getLessee() + " -> " + newVal.getLessee(), operatorUsername, operatorId);
        }
        if (!Objects.equals(oldVal.getInvoicePeriod(), newVal.getInvoicePeriod())) {
            saveLog(newVal, WarehouseActionType.EDIT, "发票租期", oldVal.getInvoicePeriod(), newVal.getInvoicePeriod(), "修改最近发票租期：" + oldVal.getInvoicePeriod() + " -> " + newVal.getInvoicePeriod(), operatorUsername, operatorId);
        }
        if (!Objects.equals(oldVal.getClosePlan(), newVal.getClosePlan())) {
            saveLog(newVal, WarehouseActionType.EDIT, "关仓计划", oldVal.getClosePlan(), newVal.getClosePlan(), "修改关仓计划", operatorUsername, operatorId);
        }
        if (!Objects.equals(oldVal.getHasPropertyCert(), newVal.getHasPropertyCert())) {
            saveLog(newVal, WarehouseActionType.EDIT, "是否有产权证", String.valueOf(oldVal.getHasPropertyCert()), String.valueOf(newVal.getHasPropertyCert()), "修改是否有产权证：" + (oldVal.getHasPropertyCert() ? "是" : "否") + " -> " + (newVal.getHasPropertyCert() ? "是" : "否"), operatorUsername, operatorId);
        }
        if (!Objects.equals(oldVal.getHasInvoice(), newVal.getHasInvoice())) {
            saveLog(newVal, WarehouseActionType.EDIT, "是否有发票", String.valueOf(oldVal.getHasInvoice()), String.valueOf(newVal.getHasInvoice()), "修改是否有发票：" + (oldVal.getHasInvoice() ? "是" : "否") + " -> " + (newVal.getHasInvoice() ? "是" : "否"), operatorUsername, operatorId);
        }
        if (!Objects.equals(oldVal.getHasPhotos(), newVal.getHasPhotos())) {
            saveLog(newVal, WarehouseActionType.EDIT, "是否有内外照片", String.valueOf(oldVal.getHasPhotos()), String.valueOf(newVal.getHasPhotos()), "修改是否有内外照片：" + (oldVal.getHasPhotos() ? "是" : "否") + " -> " + (newVal.getHasPhotos() ? "是" : "否"), operatorUsername, operatorId);
        }
        if (!Objects.equals(oldVal.getCertRemarks(), newVal.getCertRemarks())) {
            saveLog(newVal, WarehouseActionType.EDIT, "核验备注", oldVal.getCertRemarks(), newVal.getCertRemarks(), "修改资料核验备注", operatorUsername, operatorId);
        }
    }
}
