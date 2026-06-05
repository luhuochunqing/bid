package com.xiyu.bid.warehouse.dto;

import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.domain.WarehouseType;

import java.time.LocalDate;
import java.util.List;

public record WarehouseFilterDTO(
        String keyword,
        List<WarehouseType> types,
        List<WarehouseStatus> statuses,
        String province,
        LocalDate endDateFrom,
        LocalDate endDateTo,
        Boolean hasPropertyCert,
        Boolean hasInvoice,
        Boolean hasPhotos,
        String contactPersonKeyword
) {}
