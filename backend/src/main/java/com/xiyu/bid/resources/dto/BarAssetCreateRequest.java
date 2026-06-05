package com.xiyu.bid.resources.dto;

import com.xiyu.bid.resources.entity.BarAsset;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BarAssetCreateRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Type is required")
    private BarAsset.AssetType type;

    @NotNull(message = "Value is required")
    @Positive(message = "Value must be positive")
    private BigDecimal value;

    @NotNull(message = "Status is required")
    private BarAsset.AssetStatus status;

    @NotNull(message = "Acquire date is required")
    private LocalDate acquireDate;

    private String remark;
}
