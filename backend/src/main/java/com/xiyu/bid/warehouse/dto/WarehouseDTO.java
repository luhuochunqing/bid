package com.xiyu.bid.warehouse.dto;

import com.xiyu.bid.warehouse.domain.WarehouseType;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WarehouseDTO {
    @NotBlank private String name;
    @NotNull private WarehouseType type;
    @NotBlank private String region;
    @NotBlank private String province;
    @NotBlank private String address;
    @NotNull @DecimalMin("0") private BigDecimal area;
    @NotBlank private String contactPerson;
    private String remarks;

    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
    @NotBlank private String lessor;
    @NotBlank private String lessee;
    private String invoicePeriod;
    private String closePlan;

    private Boolean hasPropertyCert;
    private Boolean hasInvoice;
    private Boolean hasPhotos;
    private String certRemarks;
}
