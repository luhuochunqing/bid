package com.xiyu.bid.warehouse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseAttachmentConsistencyTest {

    @Test
    void 产权证开关开启_删除最后一个附件_应被拒绝() {
        WarehouseReadModel wh = testWarehouse(true, false, false, false);
        var result = WarehouseAttachmentConsistency.checkDeleteAllowed(
                wh, WarehouseAttachmentType.PROPERTY_CERTIFICATE, 0);
        assertThat(result).isPresent();
        assertThat(result.get()).contains("产权证");
    }

    @Test
    void 发票开关开启_删除最后一个附件_应被拒绝() {
        WarehouseReadModel wh = testWarehouse(false, true, false, false);
        var result = WarehouseAttachmentConsistency.checkDeleteAllowed(
                wh, WarehouseAttachmentType.INVOICE, 0);
        assertThat(result).isPresent();
        assertThat(result.get()).contains("发票");
    }

    @Test
    void 租赁合同开关开启_删除最后一个附件_应被拒绝() {
        WarehouseReadModel wh = testWarehouse(false, false, false, true);
        var result = WarehouseAttachmentConsistency.checkDeleteAllowed(
                wh, WarehouseAttachmentType.LEASE_CONTRACT, 0);
        assertThat(result).isPresent();
        assertThat(result.get()).contains("租赁合同");
    }

    @Test
    void 内外照片开关开启_删除最后一个附件_应被拒绝() {
        WarehouseReadModel wh = testWarehouse(false, false, true, false);
        var result = WarehouseAttachmentConsistency.checkDeleteAllowed(
                wh, WarehouseAttachmentType.PHOTOS, 0);
        assertThat(result).isPresent();
        assertThat(result.get()).contains("内外照片");
    }

    @Test
    void 开关关闭_删除最后一个附件_应允许() {
        WarehouseReadModel wh = testWarehouse(false, false, false, false);
        var result = WarehouseAttachmentConsistency.checkDeleteAllowed(
                wh, WarehouseAttachmentType.LEASE_CONTRACT, 0);
        assertThat(result).isEmpty();
    }

    @Test
    void 开关开启_删除后仍有剩余附件_应允许() {
        WarehouseReadModel wh = testWarehouse(true, false, false, true);
        var result = WarehouseAttachmentConsistency.checkDeleteAllowed(
                wh, WarehouseAttachmentType.LEASE_CONTRACT, 1);
        assertThat(result).isEmpty();
    }

    @Test
    void 多个开关同时开启_只校验当前类型() {
        WarehouseReadModel wh = testWarehouse(true, true, false, true);
        var result = WarehouseAttachmentConsistency.checkDeleteAllowed(
                wh, WarehouseAttachmentType.PROPERTY_CERTIFICATE, 0);
        assertThat(result).isPresent();
        assertThat(result.get()).contains("产权证");
    }

    @Test
    void 开关为null_视为关闭_应允许删除() {
        WarehouseReadModel wh = new WarehouseReadModel() {
            @Override public Long getId() { return 1L; }
            @Override public String getName() { return "test"; }
            @Override public WarehouseType getType() { return WarehouseType.SELF_OPERATED; }
            @Override public String getRegion() { return null; }
            @Override public String getProvince() { return null; }
            @Override public String getAddress() { return null; }
            @Override public BigDecimal getArea() { return null; }
            @Override public String getContactPerson() { return null; }
            @Override public String getRemarks() { return null; }
            @Override public LocalDate getStartDate() { return null; }
            @Override public LocalDate getEndDate() { return null; }
            @Override public String getLessor() { return null; }
            @Override public String getLessee() { return null; }
            @Override public String getInvoicePeriod() { return null; }
            @Override public LocalDate getInvoicePeriodStart() { return null; }
            @Override public LocalDate getInvoicePeriodEnd() { return null; }
            @Override public String getClosePlan() { return null; }
            @Override public String getCloseReason() { return null; }
            @Override public Boolean getHasPropertyCert() { return null; }
            @Override public Boolean getHasInvoice() { return null; }
            @Override public Boolean getHasPhotos() { return null; }
            @Override public Boolean getHasLeaseContract() { return null; }
            @Override public String getCertRemarks() { return null; }
            @Override public WarehouseStatus getStatus() { return null; }
            @Override public Long getCreatedBy() { return null; }
            @Override public LocalDateTime getCreatedAt() { return null; }
            @Override public Long getUpdatedBy() { return null; }
            @Override public LocalDateTime getUpdatedAt() { return null; }
        };
        var result = WarehouseAttachmentConsistency.checkDeleteAllowed(
                wh, WarehouseAttachmentType.LEASE_CONTRACT, 0);
        assertThat(result).isEmpty();
    }

    private WarehouseReadModel testWarehouse(boolean hasPropertyCert, boolean hasInvoice,
                                         boolean hasPhotos, boolean hasLeaseContract) {
        return new WarehouseReadModel() {
            @Override public Long getId() { return 1L; }
            @Override public String getName() { return "test"; }
            @Override public WarehouseType getType() { return WarehouseType.SELF_OPERATED; }
            @Override public String getRegion() { return "华东"; }
            @Override public String getProvince() { return "上海"; }
            @Override public String getAddress() { return "test"; }
            @Override public BigDecimal getArea() { return BigDecimal.valueOf(1000); }
            @Override public String getContactPerson() { return "张三"; }
            @Override public String getRemarks() { return null; }
            @Override public LocalDate getStartDate() { return LocalDate.of(2025, 1, 1); }
            @Override public LocalDate getEndDate() { return LocalDate.of(2026, 12, 31); }
            @Override public String getLessor() { return null; }
            @Override public String getLessee() { return null; }
            @Override public String getInvoicePeriod() { return null; }
            @Override public LocalDate getInvoicePeriodStart() { return null; }
            @Override public LocalDate getInvoicePeriodEnd() { return null; }
            @Override public String getClosePlan() { return null; }
            @Override public String getCloseReason() { return null; }
            @Override public Boolean getHasPropertyCert() { return hasPropertyCert; }
            @Override public Boolean getHasInvoice() { return hasInvoice; }
            @Override public Boolean getHasPhotos() { return hasPhotos; }
            @Override public Boolean getHasLeaseContract() { return hasLeaseContract; }
            @Override public String getCertRemarks() { return null; }
            @Override public WarehouseStatus getStatus() { return WarehouseStatus.IN_USE; }
            @Override public Long getCreatedBy() { return 1L; }
            @Override public LocalDateTime getCreatedAt() { return LocalDateTime.now(); }
            @Override public Long getUpdatedBy() { return 1L; }
            @Override public LocalDateTime getUpdatedAt() { return LocalDateTime.now(); }
        };
    }
}
