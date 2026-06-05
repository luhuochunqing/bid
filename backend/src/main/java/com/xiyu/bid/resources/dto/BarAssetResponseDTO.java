package com.xiyu.bid.resources.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class BarAssetResponseDTO {
    Long id;
    String name;
    AssetType type;
    BigDecimal value;
    AssetStatus status;
    LocalDate acquireDate;
    String remark;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public enum AssetType {
        EQUIPMENT,
        FACILITY,
        VEHICLE,
        INVENTORY,
        LICENSE,
        OTHER
    }

    public enum AssetStatus {
        AVAILABLE,
        IN_USE,
        MAINTENANCE,
        RETIRED,
        DISPOSED
    }
}
