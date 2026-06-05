package com.xiyu.bid.warehouse.domain;

import java.time.LocalDate;
import java.util.List;

public record WarehouseFilterCriteria(
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
) {
    public static WarehouseFilterCriteria empty() {
        return new WarehouseFilterCriteria(null, List.of(), List.of(), null, null, null, null, null, null, null);
    }
}
