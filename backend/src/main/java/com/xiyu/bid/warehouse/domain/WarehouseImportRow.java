package com.xiyu.bid.warehouse.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 仓库导入行解析结果（数据载体）。
 * 从 WarehouseImportPolicy.ParsedRow 拆出为顶级类以满足 300 行预算。
 */
public class WarehouseImportRow {
    public int rowIndex;
    public String[] rawCells;
    public String sanitizedName;
    public WarehouseType type;
    public String province;
    public String address;
    public BigDecimal area;
    public String region;
    public String contactPerson;
    public String remarks;
    public LocalDate startDate;
    public LocalDate endDate;
    public String lessor;
    public String lessee;
    public LocalDate invoicePeriodStart;
    public LocalDate invoicePeriodEnd;
    public String closePlan;
    public boolean hasPropertyCert;
    public boolean hasInvoice;
    public boolean hasPhotos;
    public boolean hasLeaseContract;
    public String propertyCertFile;
    public String invoiceFile;
    public String photosFile;
    public String leaseContractFileName;
    public String propertyCertExpectedName;
    public String invoiceExpectedName;
    public String photosExpectedName;
    public String leaseContractExpectedName;
    public String certRemarks;
    public List<String> errors;
    public boolean valid() { return errors == null || errors.isEmpty(); }
}
