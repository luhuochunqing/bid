package com.xiyu.bid.warehouse.dto;

import com.xiyu.bid.warehouse.domain.WarehouseActionType;

import java.time.LocalDateTime;

public record WarehouseOperationLogDTO(
        Long id,
        LocalDateTime createdAt,
        String operatorUsername,
        WarehouseActionType actionType,
        String fieldName,
        String oldValue,
        String newValue,
        String description
) {}
