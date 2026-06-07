package com.xiyu.bid.warehouse.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record WarehouseDetailDTO(
        Long id,
        String name,
        WarehouseTypeEnum type,
        String region,
        String province,
        String address,
        BigDecimal area,
        String contactPerson,
        String remarks,
        LocalDate startDate,
        LocalDate endDate,
        String lessor,
        String lessee,
        String invoicePeriod,
        String closePlan,
        String closeReason,
        Boolean hasPropertyCert,
        Boolean hasInvoice,
        Boolean hasPhotos,
        String certRemarks,
        WarehouseStatusEnum status,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version,
        List<WarehouseAttachmentDTO> attachments,
        List<WarehouseOperationLogDTO> operationLogs
) {
    public enum WarehouseTypeEnum { SELF_OPERATED, CLOUD }
    public enum WarehouseStatusEnum { IN_USE, EXPIRING, EXPIRED, CLOSED }
}
