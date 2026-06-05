package com.xiyu.bid.resources.dto;

import com.xiyu.bid.resources.entity.BarAsset;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BarAssetUpdateRequest {

    private String name;

    private BarAsset.AssetType type;

    @Positive(message = "Value must be positive")
    private BigDecimal value;

    private BarAsset.AssetStatus status;

    private LocalDate acquireDate;

    private String remark;
}
