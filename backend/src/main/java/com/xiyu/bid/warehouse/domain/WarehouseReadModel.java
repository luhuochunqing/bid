package com.xiyu.bid.warehouse.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface WarehouseReadModel {
    Long getId();
    String getName();
    WarehouseType getType();
    String getRegion();
    String getProvince();
    String getAddress();
    BigDecimal getArea();
    String getContactPerson();
    String getRemarks();
    LocalDate getStartDate();
    LocalDate getEndDate();
    String getLessor();
    String getLessee();
    String getInvoicePeriod();
    LocalDate getInvoicePeriodStart();
    LocalDate getInvoicePeriodEnd();
    String getClosePlan();
    String getCloseReason();
    Boolean getHasPropertyCert();
    Boolean getHasInvoice();
    Boolean getHasPhotos();
    String getCertRemarks();
    WarehouseStatus getStatus();
    Long getCreatedBy();
    LocalDateTime getCreatedAt();
    Long getUpdatedBy();
    LocalDateTime getUpdatedAt();
}
